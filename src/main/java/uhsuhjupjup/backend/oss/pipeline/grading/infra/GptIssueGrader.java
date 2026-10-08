package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonObject;
import com.openai.core.JsonString;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.chat.completions.ChatCompletion.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletion;
import com.openai.models.chat.completions.StructuredChatCompletion.Choice;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
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
@ConditionalOnProperty(name = "oss.grading.gpt.enabled", havingValue = "true")
class GptIssueGrader implements IssueGrader {

    private static final long MAX_COMPLETION_TOKENS = 2_048L;
    private static final BigDecimal MAX_TEMPERATURE = BigDecimal.valueOf(2);
    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(408, 409, 429);
    private static final String CALL_FAILED = "GPT 판정 호출이 실패했습니다";
    private static final String REQUEST_REJECTED = "GPT 판정 요청이 거절됐습니다";

    private final OpenAIClient openAiClient;
    private final String model;
    private final Double temperature;

    GptIssueGrader(@Qualifier(IssueGraderOpenAiConfig.ISSUE_GRADER_CLIENT) OpenAIClient openAiClient,
                   @Value("${oss.grading.gpt.model:gpt-4o-mini}") String model,
                   @Value("${oss.grading.gpt.temperature:0}") String temperature) {
        this.openAiClient = openAiClient;
        this.model = model;
        this.temperature = TemperatureSetting.parse(temperature, MAX_TEMPERATURE);
    }

    @Override
    public IssueGradingResult grade(String title, String body, List<String> labels) {
        StructuredChatCompletion<IssueGradingOutput> completion = send(userMessageOf(title, body, labels));
        logUsage(completion);
        Answer answer = answerOf(completion);
        requireCompleteAnswer(answer);
        return new IssueGradingResult(verdictOf(answer.message()), answer.model());
    }

    private static String userMessageOf(String title, String body, List<String> labels) {
        try {
            return IssueGradingPrompt.user(title, body, labels);
        } catch (RuntimeException e) {
            throw new IssueGradingException(Reason.INVALID_INPUT,
                    "GPT 판정 입력을 만들지 못했습니다(" + SafeFailureText.classNamesOf(e) + ")");
        }
    }

    private StructuredChatCompletionCreateParams<IssueGradingOutput> request(String userMessage) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(model)
                .maxCompletionTokens(MAX_COMPLETION_TOKENS)
                .addSystemMessage(IssueGradingPrompt.system())
                .addUserMessage(userMessage);
        if (temperature != null) {
            builder.temperature(temperature);
        }
        return builder.responseFormat(IssueGradingOutput.class).build();
    }

    private StructuredChatCompletion<IssueGradingOutput> send(String userMessage) {
        try {
            return openAiClient.chat().completions().create(request(userMessage));
        } catch (OpenAIServiceException e) {
            throw failureOf(e);
        } catch (LlmRetryAbortedException e) {
            throw new IssueGradingException(Reason.UNAVAILABLE, "GPT 판정 호출을 그만뒀습니다: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IssueGradingException(Reason.UNAVAILABLE,
                    CALL_FAILED + "(" + SafeFailureText.classNamesOf(e) + ")");
        }
    }

    private static void logUsage(StructuredChatCompletion<IssueGradingOutput> completion) {
        Optional<CompletionUsage> usage = completion._usage().asKnown();
        log.info("이슈 판정 응답 model={} finishReason={} input={} output={} cached={} issueId={}",
                completion._model().asKnown().orElse("none"),
                completion._choices().asKnown()
                        .flatMap(choices -> choices.stream().findFirst())
                        .flatMap(choice -> choice._finishReason().asKnown())
                        .map(FinishReason::toString)
                        .orElse("none"),
                usage.flatMap(known -> known._promptTokens().asKnown()).orElse(0L),
                usage.flatMap(known -> known._completionTokens().asKnown()).orElse(0L),
                usage.flatMap(known -> known._promptTokensDetails().asKnown())
                        .flatMap(details -> details._cachedTokens().asKnown())
                        .orElse(0L),
                issueIdInLogContext());
    }

    private static String issueIdInLogContext() {
        return Optional.ofNullable(MDC.get(IssueGrader.ISSUE_ID_LOG_KEY)).orElse("none");
    }

    private static Answer answerOf(StructuredChatCompletion<IssueGradingOutput> completion) {
        try {
            String answeredModel = completion.model();
            Choice<IssueGradingOutput> choice = completion.choices().stream()
                    .findFirst()
                    .orElseThrow(() -> new IssueGradingException(Reason.UNAVAILABLE, "GPT 응답에 선택지가 없습니다"));
            StructuredChatCompletionMessage<IssueGradingOutput> message = choice.message();
            return new Answer(answeredModel, message, choice._finishReason().asKnown(),
                    message.refusal().isPresent());
        } catch (IssueGradingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IssueGradingException(Reason.UNAVAILABLE,
                    "GPT 응답을 읽지 못했습니다(" + SafeFailureText.classNamesOf(e) + ")");
        }
    }

    private static void requireCompleteAnswer(Answer answer) {
        if (answer.refused()) {
            throw new IssueGradingException(Reason.REFUSED, "GPT가 판정을 거절했습니다(refusal)");
        }
        if (answer.finishReason().filter(FinishReason.CONTENT_FILTER::equals).isPresent()) {
            throw new IssueGradingException(Reason.REFUSED, "GPT가 판정을 거절했습니다(finish_reason=content_filter)");
        }
        if (answer.finishReason().filter(FinishReason.LENGTH::equals).isPresent()) {
            throw new IssueGradingException(Reason.TRUNCATED,
                    "GPT 판정 출력이 max_completion_tokens " + MAX_COMPLETION_TOKENS + "에서 잘렸습니다");
        }
    }

    private static OssIssueVerdict verdictOf(StructuredChatCompletionMessage<IssueGradingOutput> message) {
        IssueGradingOutput output = outputOf(message);
        try {
            return IssueGradingOutputValidator.validate(output);
        } catch (InvalidIssueGradingOutputException e) {
            throw new IssueGradingException(Reason.INVALID_OUTPUT,
                    "GPT 판정 출력이 규칙을 어겼습니다: " + describe(e.violations()), e);
        } catch (RuntimeException e) {
            throw unreadableOutput(e);
        }
    }

    private static IssueGradingOutput outputOf(StructuredChatCompletionMessage<IssueGradingOutput> message) {
        if (message.rawMessage()._content().asKnown().isEmpty()) {
            throw new IssueGradingException(Reason.UNAVAILABLE, "GPT 응답에 판정 출력이 없습니다");
        }
        try {
            return message.content().orElseThrow();
        } catch (RuntimeException e) {
            throw unreadableOutput(e);
        }
    }

    private static String describe(List<Violation> violations) {
        return violations.stream().map(Violation::toString).collect(Collectors.joining(", "));
    }

    private static IssueGradingException unreadableOutput(RuntimeException e) {
        return new IssueGradingException(Reason.INVALID_OUTPUT,
                "GPT 판정 출력을 읽지 못했습니다(" + SafeFailureText.classNamesOf(e) + ")");
    }

    private static IssueGradingException failureOf(OpenAIServiceException e) {
        int status = e.statusCode();
        boolean clientError = status >= 400 && status < 500;
        JsonValue error = e.body();
        String detail = "(상태 " + status + errorTypeOf(error) + ")"
                + (SafeFailureText.showsApiMessage(status) ? apiMessageOf(error) : "");
        if (clientError && !RETRYABLE_CLIENT_ERRORS.contains(status)) {
            return new IssueGradingException(Reason.REJECTED, REQUEST_REJECTED + detail);
        }
        return new IssueGradingException(Reason.UNAVAILABLE, CALL_FAILED + detail);
    }

    private static String errorTypeOf(JsonValue error) {
        return textOf(error, "type").map(type -> ", " + type).orElse("");
    }

    private static String apiMessageOf(JsonValue error) {
        return textOf(error, "message").map(message -> ": " + message).orElse("");
    }

    private static Optional<String> textOf(JsonValue error, String field) {
        if (error instanceof JsonObject fields
                && fields.values().get(field) instanceof JsonString text
                && !text.value().isBlank()) {
            return Optional.of(SafeFailureText.excerptOf(text.value()));
        }
        return Optional.empty();
    }

    private record Answer(String model, StructuredChatCompletionMessage<IssueGradingOutput> message,
                          Optional<FinishReason> finishReason, boolean refused) {
    }
}
