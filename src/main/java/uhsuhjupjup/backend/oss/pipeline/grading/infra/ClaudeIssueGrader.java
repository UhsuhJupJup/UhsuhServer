package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonObject;
import com.anthropic.core.JsonString;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.config.llm.LlmRetryAbortedException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGrader;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.InvalidIssueGradingOutputException.Violation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(name = "oss.grading.claude.enabled", havingValue = "true")
class ClaudeIssueGrader implements IssueGrader {

    private static final long MAX_TOKENS = 2_048L;
    private static final BigDecimal MAX_TEMPERATURE = BigDecimal.ONE;
    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(408, 409, 429);
    private static final String CALL_FAILED = "Claude 판정 호출이 실패했습니다";
    private static final String REQUEST_REJECTED = "Claude 판정 요청이 거절됐습니다";

    private final AnthropicClient anthropicClient;
    private final String model;
    private final Double temperature;

    ClaudeIssueGrader(@Qualifier(IssueGraderAnthropicConfig.ISSUE_GRADER_CLIENT) AnthropicClient anthropicClient,
                      @Value("${oss.grading.claude.model:claude-haiku-4-5}") String model,
                      @Value("${oss.grading.claude.temperature:0}") String temperature) {
        this.anthropicClient = anthropicClient;
        this.model = model;
        this.temperature = TemperatureSetting.parse(temperature, MAX_TEMPERATURE);
    }

    @Override
    public IssueGradingResult grade(String title, String body, List<String> labels) {
        StructuredMessage<IssueGradingOutput> message = send(userMessageOf(title, body, labels));
        logUsage(message);
        String answeredModel = answeredModelOf(message);
        requireCompleteAnswer(message);
        return new IssueGradingResult(verdictOf(textBlockOf(message)), answeredModel);
    }

    private static String userMessageOf(String title, String body, List<String> labels) {
        try {
            return IssueGradingPrompt.user(title, body, labels);
        } catch (RuntimeException e) {
            throw new IssueGradingException(Reason.INVALID_INPUT,
                    "Claude 판정 입력을 만들지 못했습니다(" + SafeFailureText.classNamesOf(e) + ")");
        }
    }

    private StructuredMessageCreateParams<IssueGradingOutput> request(String userMessage) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(IssueGradingPrompt.system())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(userMessage);
        applyTemperature(builder);
        return builder.outputConfig(IssueGradingOutput.class).build();
    }

    @SuppressWarnings("deprecation")
    private void applyTemperature(MessageCreateParams.Builder builder) {
        if (temperature != null) {
            builder.temperature(temperature);
        }
    }

    private StructuredMessage<IssueGradingOutput> send(String userMessage) {
        try {
            return anthropicClient.messages().create(request(userMessage));
        } catch (AnthropicServiceException e) {
            throw failureOf(e);
        } catch (LlmRetryAbortedException e) {
            throw new IssueGradingException(Reason.UNAVAILABLE, "Claude 판정 호출을 그만뒀습니다: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IssueGradingException(Reason.UNAVAILABLE,
                    CALL_FAILED + "(" + SafeFailureText.classNamesOf(e) + ")");
        }
    }

    private static void logUsage(StructuredMessage<IssueGradingOutput> message) {
        Optional<Usage> usage = message._usage().asKnown();
        log.info("이슈 판정 응답 model={} stopReason={} input={} output={} cacheWrite={} cacheRead={} issueId={}",
                message._model().asKnown().map(Model::toString).orElse("none"),
                message._stopReason().asKnown().map(StopReason::toString).orElse("none"),
                usage.flatMap(known -> known._inputTokens().asKnown()).orElse(0L),
                usage.flatMap(known -> known._outputTokens().asKnown()).orElse(0L),
                usage.flatMap(known -> known._cacheCreationInputTokens().asKnown()).orElse(0L),
                usage.flatMap(known -> known._cacheReadInputTokens().asKnown()).orElse(0L),
                issueIdInLogContext());
    }

    private static String issueIdInLogContext() {
        return Optional.ofNullable(MDC.get(IssueGrader.ISSUE_ID_LOG_KEY)).orElse("none");
    }

    private static String answeredModelOf(StructuredMessage<IssueGradingOutput> message) {
        try {
            return message.model().asString();
        } catch (RuntimeException e) {
            throw unreadableAnswer(e);
        }
    }

    private static void requireCompleteAnswer(StructuredMessage<IssueGradingOutput> message) {
        Optional<StopReason> stopReason = message._stopReason().asKnown();
        if (stopReason.filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new IssueGradingException(Reason.REFUSED, "Claude가 판정을 거절했습니다(stop_reason=refusal)");
        }
        if (stopReason.filter(StopReason.MAX_TOKENS::equals).isPresent()) {
            throw new IssueGradingException(Reason.TRUNCATED,
                    "Claude 판정 출력이 max_tokens " + MAX_TOKENS + "에서 잘렸습니다");
        }
    }

    private static StructuredTextBlock<IssueGradingOutput> textBlockOf(StructuredMessage<IssueGradingOutput> message) {
        try {
            return message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .filter(text -> text.rawTextBlock()._text().asKnown().isPresent())
                    .findFirst()
                    .orElseThrow(() -> new IssueGradingException(Reason.UNAVAILABLE, "Claude 응답에 판정 출력이 없습니다"));
        } catch (IssueGradingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unreadableAnswer(e);
        }
    }

    private static OssIssueVerdict verdictOf(StructuredTextBlock<IssueGradingOutput> text) {
        IssueGradingOutput output = outputOf(text);
        try {
            return IssueGradingOutputValidator.validate(output);
        } catch (InvalidIssueGradingOutputException e) {
            throw new IssueGradingException(Reason.INVALID_OUTPUT,
                    "Claude 판정 출력이 규칙을 어겼습니다: " + describe(e.violations()), e);
        } catch (RuntimeException e) {
            throw unreadableOutput(e);
        }
    }

    private static IssueGradingOutput outputOf(StructuredTextBlock<IssueGradingOutput> text) {
        try {
            return text.text();
        } catch (RuntimeException e) {
            throw unreadableOutput(e);
        }
    }

    private static String describe(List<Violation> violations) {
        return violations.stream().map(Violation::toString).collect(Collectors.joining(", "));
    }

    private static IssueGradingException unreadableAnswer(RuntimeException e) {
        return new IssueGradingException(Reason.UNAVAILABLE,
                "Claude 응답을 읽지 못했습니다(" + SafeFailureText.classNamesOf(e) + ")");
    }

    private static IssueGradingException unreadableOutput(RuntimeException e) {
        return new IssueGradingException(Reason.INVALID_OUTPUT,
                "Claude 판정 출력을 읽지 못했습니다(" + SafeFailureText.classNamesOf(e) + ")");
    }

    private static IssueGradingException failureOf(AnthropicServiceException e) {
        int status = e.statusCode();
        boolean clientError = status >= 400 && status < 500;
        String detail = "(상태 " + status + errorTypeOf(e) + ")"
                + (SafeFailureText.showsApiMessage(status) ? apiMessageOf(e.body()) : "");
        if (clientError && !RETRYABLE_CLIENT_ERRORS.contains(status)) {
            return new IssueGradingException(Reason.REJECTED, REQUEST_REJECTED + detail);
        }
        return new IssueGradingException(Reason.UNAVAILABLE, CALL_FAILED + detail);
    }

    private static String errorTypeOf(AnthropicServiceException e) {
        return e.errorType().map(type -> ", " + type.value()).orElse("");
    }

    private static String apiMessageOf(JsonValue body) {
        if (body instanceof JsonObject fields
                && fields.values().get("error") instanceof JsonObject error
                && error.values().get("message") instanceof JsonString message
                && !message.value().isBlank()) {
            return ": " + SafeFailureText.excerptOf(message.value());
        }
        return "";
    }
}
