package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonObject;
import com.anthropic.core.JsonString;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.NestedExceptionUtils;
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
    private static final String INVALID_TEMPERATURE = "이슈 판정 temperature는 비우거나 0 이상 1 이하의 숫자여야 합니다: ";
    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(408, 409, 429);
    private static final int MAX_API_MESSAGE_CODE_POINTS = 200;
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
        this.temperature = temperatureOf(temperature);
    }

    @Override
    public IssueGradingResult grade(String title, String body, List<String> labels) {
        StructuredMessage<IssueGradingOutput> message = send(title, body, labels);
        try {
            String answeredModel = message.model().asString();
            logUsage(answeredModel, message);
            requireCompleteAnswer(message);
            return new IssueGradingResult(verdictOf(message), answeredModel);
        } catch (IssueGradingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IssueGradingException(Reason.INVALID_OUTPUT,
                    "Claude 판정 응답을 읽지 못했습니다(" + classNamesOf(e) + ")");
        }
    }

    private StructuredMessageCreateParams<IssueGradingOutput> request(String title, String body, List<String> labels) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(IssueGradingPrompt.system())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(IssueGradingPrompt.user(title, body, labels));
        applyTemperature(builder);
        return builder.outputConfig(IssueGradingOutput.class).build();
    }

    @SuppressWarnings("deprecation")
    private void applyTemperature(MessageCreateParams.Builder builder) {
        if (temperature != null) {
            builder.temperature(temperature);
        }
    }

    private StructuredMessage<IssueGradingOutput> send(String title, String body, List<String> labels) {
        try {
            return anthropicClient.messages().create(request(title, body, labels));
        } catch (AnthropicServiceException e) {
            throw failureOf(e);
        } catch (LlmRetryAbortedException e) {
            throw new IssueGradingException(Reason.UNAVAILABLE, "Claude 판정 호출을 그만뒀습니다: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IssueGradingException(Reason.UNAVAILABLE, CALL_FAILED + "(" + classNamesOf(e) + ")");
        }
    }

    private static void logUsage(String answeredModel, StructuredMessage<IssueGradingOutput> message) {
        Usage usage = message.usage();
        log.info("이슈 판정 응답 model={} stopReason={} input={} output={} cacheWrite={} cacheRead={}",
                answeredModel,
                message.stopReason().map(StopReason::toString).orElse("none"),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheCreationInputTokens().orElse(0L),
                usage.cacheReadInputTokens().orElse(0L));
    }

    private static void requireCompleteAnswer(StructuredMessage<IssueGradingOutput> message) {
        Optional<StopReason> stopReason = message.stopReason();
        if (stopReason.filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new IssueGradingException(Reason.REFUSED, "Claude가 판정을 거절했습니다(stop_reason=refusal)");
        }
        if (stopReason.filter(StopReason.MAX_TOKENS::equals).isPresent()) {
            throw new IssueGradingException(Reason.TRUNCATED,
                    "Claude 판정 출력이 max_tokens " + MAX_TOKENS + "에서 잘렸습니다");
        }
    }

    private static OssIssueVerdict verdictOf(StructuredMessage<IssueGradingOutput> message) {
        IssueGradingOutput output = message.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(StructuredTextBlock::text)
                .orElseThrow(() -> new IssueGradingException(Reason.INVALID_OUTPUT, "Claude 응답에 판정 출력이 없습니다"));
        try {
            return IssueGradingOutputValidator.validate(output);
        } catch (InvalidIssueGradingOutputException e) {
            throw new IssueGradingException(Reason.INVALID_OUTPUT,
                    "Claude 판정 출력이 규칙을 어겼습니다: " + describe(e.violations()), e);
        }
    }

    private static String describe(List<Violation> violations) {
        return violations.stream().map(Violation::toString).collect(Collectors.joining(", "));
    }

    private static IssueGradingException failureOf(AnthropicServiceException e) {
        int status = e.statusCode();
        boolean clientError = status >= 400 && status < 500;
        String detail = "(상태 " + status + errorTypeOf(e) + ")" + (clientError ? apiMessageOf(e.body()) : "");
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
            return ": " + excerptOf(message.value());
        }
        return "";
    }

    private static String excerptOf(String message) {
        StringBuilder excerpt = new StringBuilder();
        message.codePoints()
                .limit(MAX_API_MESSAGE_CODE_POINTS)
                .map(codePoint -> isLineBreakOrControl(codePoint) ? ' ' : codePoint)
                .forEach(excerpt::appendCodePoint);
        return excerpt.toString();
    }

    private static boolean isLineBreakOrControl(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static String classNamesOf(Throwable failure) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
        String name = failure.getClass().getSimpleName();
        return root == failure ? name : name + ", " + root.getClass().getSimpleName();
    }

    private static Double temperatureOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        BigDecimal temperature = parseTemperature(value);
        if (temperature.compareTo(BigDecimal.ZERO) < 0 || temperature.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(INVALID_TEMPERATURE + value);
        }
        return temperature.doubleValue();
    }

    private static BigDecimal parseTemperature(String value) {
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(INVALID_TEMPERATURE + value, e);
        }
    }
}
