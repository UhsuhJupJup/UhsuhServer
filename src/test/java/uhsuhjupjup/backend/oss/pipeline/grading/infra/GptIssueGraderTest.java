package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import uhsuhjupjup.backend.config.llm.LlmCallLimits;
import uhsuhjupjup.backend.config.llm.LlmRetryAbortedException;
import uhsuhjupjup.backend.config.llm.MockLlmServer;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.stall;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.status;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.trickle;

@ExtendWith(OutputCaptureExtension.class)
class GptIssueGraderTest {

    private static final String REQUESTED_MODEL = "gpt-4o-mini";
    private static final String ANSWERED_MODEL = "gpt-4o-mini-2024-07-18";
    private static final String DEFAULT_TEMPERATURE = "0";
    private static final String ISSUE_BODY_MARKER = "ISSUEBODYMARKER";
    private static final String OUTPUT_MARKER = "OUTPUTMARKER";
    private static final String API_MESSAGE_MARKER = "APIMESSAGEMARKER";
    private static final String OTHER_FIELD_MARKER = "OTHERFIELDMARKER";
    private static final String TITLE = "Retry interval is ignored";
    private static final String BODY = "Steps:\n1. Set retry.interval: 5s\n2. Run a failing job\n" + ISSUE_BODY_MARKER;
    private static final List<String> LABELS = List.of("bug", "help wanted");
    private static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(2);
    private static final LlmCallLimits LIMITS =
            new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(5), 1, MAX_RETRY_WAIT);
    private static final int SHOWN_API_MESSAGE_CODE_POINTS = 200;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<OpenAIClient> clients = new ArrayList<>();
    private MockLlmServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockLlmServer();
    }

    @AfterEach
    void tearDown() {
        clients.forEach(OpenAIClient::close);
        server.stop();
    }

    @Test
    void grade_validOutput_returnsTheVerdictAndTheModelThatAnswered() {
        server.respondInOrder(status(200, answer("stop", validOutput().toString())));

        IssueGradingResult result = grader(LIMITS).grade(TITLE, BODY, LABELS);

        assertThat(result.verdict()).isEqualTo(new OssIssueVerdict(OssIssueDifficulty.MEDIUM,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.ABSENT, OssIssueEvidence.PARTIAL,
                true, null,
                "재현 절차는 있지만 원인이 없다.", "Steps are given but the cause is missing.",
                "종료 훅 순서 때문에 워커가 남는다.", "Workers linger because of the shutdown hook order."));
        assertThat(result.model()).isEqualTo(ANSWERED_MODEL);
    }

    @Test
    void grade_sendsTheGradingPromptWithTheDecidedSettings() throws Exception {
        server.respondInOrder(status(200, answer("stop", validOutput().toString())));

        grader(LIMITS).grade(TITLE, BODY, LABELS);

        JsonNode request = JSON.readTree(server.requestBody(0));
        assertThat(request.path("model").asText()).isEqualTo(REQUESTED_MODEL);
        assertThat(request.path("temperature").isNumber()).isTrue();
        assertThat(request.path("temperature").asDouble()).isZero();
        assertThat(request.path("max_completion_tokens").asLong()).isEqualTo(2_048L);
        assertThat(request.has("max_tokens")).isFalse();
        assertThat(request.path("messages")).hasSize(2);
        assertThat(request.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(request.path("messages").get(0).path("content").asText()).isEqualTo(IssueGradingPrompt.system());
        assertThat(request.path("messages").get(1).path("role").asText()).isEqualTo("user");
        assertThat(request.path("messages").get(1).path("content").asText())
                .isEqualTo(IssueGradingPrompt.user(TITLE, BODY, LABELS));
        assertThat(request.at("/response_format/type").asText()).isEqualTo("json_schema");
        assertThat(request.at("/response_format/json_schema/strict").asBoolean()).isTrue();
        assertThat(request.at("/response_format/json_schema/schema")).isEqualTo(outputSchema());
    }

    @ParameterizedTest
    @CsvSource({"0.7, 0.7", "1.5, 1.5", "2, 2.0", "' 0.3 ', 0.3"})
    void grade_temperatureSetting_isSentAsGiven(String temperature, double sent) throws Exception {
        server.respondInOrder(status(200, answer("stop", validOutput().toString())));

        grader(LIMITS, temperature).grade(TITLE, BODY, LABELS);

        assertThat(JSON.readTree(server.requestBody(0)).path("temperature").asDouble()).isEqualTo(sent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void grade_blankTemperatureSetting_leavesTemperatureOutOfTheRequest(String temperature) throws Exception {
        server.respondInOrder(status(200, answer("stop", validOutput().toString())));

        grader(LIMITS, temperature).grade(TITLE, BODY, LABELS);

        assertThat(JSON.readTree(server.requestBody(0)).has("temperature")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "2.01", "3", "abc", "NaN", "0.5f"})
    void newGrader_temperatureOutOfRangeOrNotANumber_isRefused(String temperature) {
        OpenAIClient client = client(LIMITS);

        assertThatThrownBy(() -> new GptIssueGrader(client, REQUESTED_MODEL, temperature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(temperature)
                .hasMessageContaining("0 이상 2 이하");
    }

    @Test
    void grade_choicesInLowerCase_areAccepted() {
        ObjectNode output = validOutput()
                .put("evidenceCause", "present").put("evidenceFixDirection", "present")
                .put("evidenceProblem", "present").put("evidenceReproduction", "present")
                .put("exclusion", "none").put("level", "easy");
        server.respondInOrder(status(200, answer("stop", output.toString())));

        OssIssueVerdict verdict = grader(LIMITS).grade(TITLE, BODY, LABELS).verdict();

        assertThat(verdict.difficulty()).isEqualTo(OssIssueDifficulty.EASY);
        assertThat(List.of(verdict.cause(), verdict.fixDirection(), verdict.problem(), verdict.reproduction()))
                .containsOnly(OssIssueEvidence.PRESENT);
        assertThat(verdict.exclusion()).isNull();
    }

    @Test
    void grade_excludedWithoutSummaries_isAccepted() {
        ObjectNode output = validOutput().put("exclusion", "SPAM").put("level", "HARD")
                .putNull("summaryEn").putNull("summaryKo");
        server.respondInOrder(status(200, answer("stop", output.toString())));

        OssIssueVerdict verdict = grader(LIMITS).grade(TITLE, BODY, LABELS).verdict();

        assertThat(verdict.exclusion()).isEqualTo(OssIssueGradeExclusion.SPAM);
        assertThat(verdict.summaryEn()).isNull();
        assertThat(verdict.summaryKo()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outputsBreakingTheSchemaOrTheRules")
    void grade_outputBreakingTheSchemaOrTheRules_failsAsInvalidOutput(String broken, String output) {
        server.respondInOrder(status(200, answer("stop", output)));

        assertThatThrownBy(() -> grader(LIMITS).grade(TITLE, BODY, LABELS))
                .isInstanceOfSatisfying(IssueGradingException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID_OUTPUT));
    }

    static Stream<Arguments> outputsBreakingTheSchemaOrTheRules() {
        return Stream.of(
                Arguments.of("모르는 난이도", validOutput().put("level", "TRIVIAL").toString()),
                Arguments.of("모르는 근거 값", validOutput().put("evidenceCause", "SOMETIMES").toString()),
                Arguments.of("빠진 필드", without("reasonKo")),
                Arguments.of("문자열로 온 불리언", validOutput().put("evidenceRelatedPr", "maybe").toString()),
                Arguments.of("스키마에 없는 필드", validOutput().put("confidence", 0.9).toString()),
                Arguments.of("요약 없는 추천", validOutput().putNull("summaryEn").putNull("summaryKo").toString()),
                Arguments.of("링크가 든 이유", validOutput().put("reasonEn", "See https://example.com first.").toString()),
                Arguments.of("JSON이 아닌 출력", "The issue looks MEDIUM to me."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", " null\n"})
    void grade_outputThatIsJsonNull_failsAsInvalidOutput(String output) {
        server.respondInOrder(status(200, answer("stop", output)));

        assertThatThrownBy(() -> grader(LIMITS).grade(TITLE, BODY, LABELS))
                .isInstanceOfSatisfying(IssueGradingException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.INVALID_OUTPUT);
                    assertThat(e).hasNoCause();
                });
    }

    @Test
    void grade_unknownChoice_namesTheFieldWithoutQuotingTheValue() {
        server.respondInOrder(status(200, answer("stop", validOutput().put("level", "TRIVIAL").toString())));

        assertThatThrownBy(() -> grader(LIMITS).grade(TITLE, BODY, LABELS))
                .isInstanceOfSatisfying(IssueGradingException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.INVALID_OUTPUT);
                    assertThat(e.getMessage()).contains("level MISSING").doesNotContain("TRIVIAL");
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outputsTheParserWouldQuote")
    void grade_unparsableOutput_keepsItOutOfTheMessageCausesAndLogs(String where, String output,
                                                                   CapturedOutput logs) {
        server.respondInOrder(status(200, answer("stop", output)));

        Throwable failure = catchThrowable(() -> grader(LIMITS).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.INVALID_OUTPUT);
            assertThat(e.getMessage()).contains("OpenAIInvalidDataException");
            assertThat(e).hasNoCause();
        });
        assertThat(textsOf(failure)).noneMatch(text -> text.contains(OUTPUT_MARKER));
        assertThat(logs).doesNotContain(OUTPUT_MARKER);
    }

    static Stream<Arguments> outputsTheParserWouldQuote() {
        return Stream.of(
                Arguments.of("첫 토큰", OUTPUT_MARKER + "xyz"),
                Arguments.of("불리언 값 자리", validOutput().put("evidenceRelatedPr", OUTPUT_MARKER + "value").toString()),
                Arguments.of("필드 이름", validOutput().put(OUTPUT_MARKER + "field", 1).toString()),
                Arguments.of("잘린 JSON", "{\"level\": \"EASY\", \"reasonEn\": \"" + OUTPUT_MARKER));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("answersWithABrokenEnvelope")
    void grade_answerWithABrokenEnvelope_failsAsUnavailable(String broken, String answer) {
        server.respondInOrder(status(200, answer));

        assertThatThrownBy(() -> grader(LIMITS).grade(TITLE, BODY, LABELS))
                .isInstanceOfSatisfying(IssueGradingException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                    assertThat(e).hasNoCause();
                });
    }

    static Stream<Arguments> answersWithABrokenEnvelope() {
        ObjectNode withoutMessage = envelope();
        withoutMessage.putArray("choices").addObject().put("index", 0).put("finish_reason", "stop");
        ObjectNode refusalNotText = envelope();
        choice(refusalNotText, "stop").put("content", validOutput().toString()).put("refusal", 7);
        return Stream.of(
                Arguments.of("출력 없음", answerWithoutContent("stop")),
                Arguments.of("선택지가 빔", answerWithoutChoices()),
                Arguments.of("choices 없음", answerWithout("choices")),
                Arguments.of("model 없음", answerWithout("model")),
                Arguments.of("message 없음", withoutMessage.toString()),
                Arguments.of("refusal이 문자열이 아님", refusalNotText.toString()));
    }

    @Test
    void grade_answerWithoutChoices_logsNoFinishReason(CapturedOutput logs) {
        server.respondInOrder(status(200, answerWithoutChoices()));

        assertThatThrownBy(() -> grader(LIMITS).grade(TITLE, BODY, LABELS))
                .isInstanceOf(IssueGradingException.class);
        assertThat(logs).contains("finishReason=none");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("answersWithUnreadableUsage")
    void grade_unreadableUsage_stillGradesAndLogsZeros(String broken, String answer, CapturedOutput logs) {
        server.respondInOrder(status(200, answer));

        IssueGradingResult result = grader(LIMITS).grade(TITLE, BODY, LABELS);

        assertThat(result.model()).isEqualTo(ANSWERED_MODEL);
        assertThat(logs).contains("이슈 판정 응답 model=" + ANSWERED_MODEL
                + " finishReason=stop input=0 output=0 cached=0");
    }

    static Stream<Arguments> answersWithUnreadableUsage() {
        ObjectNode brokenUsage = answerNode("stop", validOutput().toString());
        brokenUsage.put("usage", "broken");
        ObjectNode brokenTokens = answerNode("stop", validOutput().toString());
        brokenTokens.putObject("usage").put("prompt_tokens", "many").put("completion_tokens", "few")
                .putObject("prompt_tokens_details").put("cached_tokens", "some");
        return Stream.of(
                Arguments.of("usage 없음", answerWithout("usage")),
                Arguments.of("usage가 객체가 아님", brokenUsage.toString()),
                Arguments.of("토큰 수가 숫자가 아님", brokenTokens.toString()));
    }

    @Test
    void grade_inputThatCannotBeBuilt_failsAsInvalidInputWithoutCallingTheApi() {
        try (MockedStatic<IssueGradingPrompt> prompt = mockStatic(IssueGradingPrompt.class)) {
            prompt.when(() -> IssueGradingPrompt.user(any(), any(), any()))
                    .thenThrow(new IllegalStateException("broken " + ISSUE_BODY_MARKER));

            Throwable failure = catchThrowable(() -> grader(LIMITS).grade(TITLE, BODY, LABELS));

            assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
                assertThat(e.getReason()).isEqualTo(Reason.INVALID_INPUT);
                assertThat(e.getMessage()).contains("IllegalStateException").doesNotContain(ISSUE_BODY_MARKER);
                assertThat(e).hasNoCause();
            });
        }
        assertThat(server.requestCount()).isZero();
    }

    @Test
    void grade_refusal_failsAsRefusedWithoutQuotingTheRefusal(CapturedOutput logs) {
        server.respondInOrder(status(200, refusal("I can't help with grading this issue. " + OUTPUT_MARKER)));

        Throwable failure = catchThrowable(() -> grader(LIMITS).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.REFUSED);
            assertThat(e.getMessage()).contains("refusal");
        });
        assertThat(textsOf(failure)).noneMatch(text -> text.contains(OUTPUT_MARKER));
        assertThat(logs).contains("finishReason=stop").doesNotContain(OUTPUT_MARKER);
    }

    @Test
    void grade_refusalIsCheckedBeforeAValidLookingOutputIsRead() {
        ObjectNode answer = envelope();
        choice(answer, "stop")
                .put("content", validOutput().toString())
                .put("refusal", "I can't help with that.");
        server.respondInOrder(status(200, answer.toString()));

        assertThatThrownBy(() -> grader(LIMITS).grade(TITLE, BODY, LABELS))
                .isInstanceOfSatisfying(IssueGradingException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.REFUSED));
    }

    @Test
    void grade_contentFilter_failsAsRefusedWithoutQuotingTheOutput(CapturedOutput logs) {
        server.respondInOrder(status(200, answer("content_filter", "{\"level\": \"EASY\", \"reasonEn\": \""
                + OUTPUT_MARKER)));

        Throwable failure = catchThrowable(() -> grader(LIMITS).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.REFUSED);
            assertThat(e.getMessage()).contains("content_filter");
        });
        assertThat(textsOf(failure)).noneMatch(text -> text.contains(OUTPUT_MARKER));
        assertThat(logs).contains("finishReason=content_filter").doesNotContain(OUTPUT_MARKER);
    }

    @Test
    void grade_outputCutAtTheTokenLimit_failsAsTruncated() {
        String cut = validOutput().toString().substring(0, 60);
        server.respondInOrder(status(200, answer("length", cut)));

        assertThatThrownBy(() -> grader(LIMITS).grade(TITLE, BODY, LABELS))
                .isInstanceOfSatisfying(IssueGradingException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.TRUNCATED);
                    assertThat(e.getMessage()).contains("2048");
                });
    }

    @ParameterizedTest
    @CsvSource({"content_filter, REFUSED", "length, TRUNCATED"})
    void grade_finishReasonIsCheckedBeforeAValidLookingOutputIsRead(String finishReason, Reason reason) {
        server.respondInOrder(status(200, answer(finishReason, validOutput().toString())));

        assertThatThrownBy(() -> grader(LIMITS).grade(TITLE, BODY, LABELS))
                .isInstanceOfSatisfying(IssueGradingException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "400, REJECTED, 1",
            "401, REJECTED, 1",
            "403, REJECTED, 1",
            "404, REJECTED, 1",
            "408, UNAVAILABLE, 2",
            "409, UNAVAILABLE, 2",
            "413, REJECTED, 1",
            "422, REJECTED, 1",
            "429, UNAVAILABLE, 2",
            "500, UNAVAILABLE, 2",
            "503, UNAVAILABLE, 2"})
    void grade_errorStatus_isRejectedOnlyWhenRetryingCannotHelp(int statusCode, Reason reason, int requests) {
        server.respondInOrder(status(statusCode, errorBody("server_error", "Request failed")));

        Throwable failure = catchThrowable(() -> grader(LIMITS).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(reason);
            assertThat(e.getMessage()).contains("상태 " + statusCode);
            assertThat(e).hasNoCause();
        });
        assertThat(server.requestCount()).isEqualTo(requests);
    }

    @ParameterizedTest
    @CsvSource({"400, invalid_request_error", "429, requests"})
    void grade_clientError_showsTheStartOfTheApiMessageOnOneLine(int statusCode, String errorType) {
        String apiMessage = "첫 줄\n둘째 줄\r\n셋째\t끝" + "😀".repeat(300) + API_MESSAGE_MARKER;
        server.respondInOrder(status(statusCode, errorBody(errorType, apiMessage)));

        Throwable failure = catchThrowable(() -> grader(LIMITS).grade(TITLE, BODY, LABELS));

        String head = "첫 줄 둘째 줄  셋째 끝";
        String shown = head + "😀".repeat(SHOWN_API_MESSAGE_CODE_POINTS - head.codePointCount(0, head.length()));
        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getMessage()).endsWith("(상태 " + statusCode + ", " + errorType + "): " + shown);
            assertThat(e.getMessage()).doesNotContain(API_MESSAGE_MARKER, "\n", "\r", "\t");
            assertThat(e).hasNoCause();
        });
        assertThat(textsOf(failure)).noneMatch(text -> text.contains(OTHER_FIELD_MARKER));
    }

    @ParameterizedTest
    @CsvSource({"401, invalid_request_error", "403, request_forbidden"})
    void grade_credentialFailure_leavesTheApiMessageOut(int statusCode, String errorType) {
        String apiMessage = "Incorrect API key provided: sk-****" + API_MESSAGE_MARKER;
        server.respondInOrder(status(statusCode, errorBody(errorType, apiMessage)));

        Throwable failure = catchThrowable(() -> grader(LIMITS).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.REJECTED);
            assertThat(e.getMessage()).endsWith("(상태 " + statusCode + ", " + errorType + ")");
        });
        assertThat(textsOf(failure))
                .noneMatch(text -> text.contains(API_MESSAGE_MARKER) || text.contains(OTHER_FIELD_MARKER));
    }

    @Test
    void grade_serverError_leavesTheApiMessageOut() {
        server.respondInOrder(status(503, errorBody("server_error", "Overloaded " + API_MESSAGE_MARKER)));

        Throwable failure = catchThrowable(() -> grader(LIMITS).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
            assertThat(e.getMessage()).endsWith("(상태 503, server_error)");
        });
        assertThat(textsOf(failure))
                .noneMatch(text -> text.contains(API_MESSAGE_MARKER) || text.contains(OTHER_FIELD_MARKER));
    }

    @Test
    void grade_rateLimitedWithLongRetryAfter_failsAtOnceAsUnavailable() {
        server.respondInOrder(
                status(429, errorBody("requests", "Slow down"), Map.of("Retry-After", "30")),
                status(200, answer("stop", validOutput().toString())));

        Throwable failure = catchThrowable(() -> grader(LIMITS).grade(TITLE, BODY, LABELS));
        Duration elapsed = server.sinceFirstRequest();

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
            assertThat(e.getCause()).isInstanceOf(LlmRetryAbortedException.class);
        });
        assertThat(server.requestCount()).isEqualTo(1);
        assertThat(elapsed).isLessThan(MAX_RETRY_WAIT);
    }

    @Test
    void grade_serverNotAnswering_failsWithinTheCallLimitAfterOneRetry() {
        LlmCallLimits impatient = new LlmCallLimits(Duration.ofSeconds(1), Duration.ofMillis(300), 1, MAX_RETRY_WAIT);
        server.respondInOrder(stall());

        Throwable failure = catchThrowable(() -> grader(impatient).grade(TITLE, BODY, LABELS));
        Duration elapsed = server.sinceFirstRequest();

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
            assertThat(e.getMessage()).contains("OpenAIIoException");
        });
        assertThat(server.requestCount()).isEqualTo(2);
        assertThat(elapsed).isLessThan(worstCase(impatient));
    }

    @Test
    void grade_answerTricklingPastTheCallLimit_failsAsUnavailableNearTheLimit() {
        LlmCallLimits shortCall = new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(1), 1, MAX_RETRY_WAIT);
        server.respondInOrder(trickle(200, answer("stop", validOutput().toString()),
                Duration.ofMillis(100), Duration.ofSeconds(6)));

        Throwable failure = catchThrowable(() -> grader(shortCall).grade(TITLE, BODY, LABELS));
        Duration elapsed = server.sinceFirstRequest();

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
            assertThat(e.getMessage()).contains("OpenAIInvalidDataException");
            assertThat(e).hasNoCause();
        });
        assertThat(server.requestCount()).isEqualTo(1);
        assertThat(elapsed).isBetween(shortCall.callTimeout().dividedBy(2), shortCall.callTimeout().plusSeconds(1));
    }

    @Test
    void grade_logsTheAnsweringModelAndTokenUsageButNotTheIssueOrTheOutput(CapturedOutput logs) {
        ObjectNode output = validOutput().put("summaryEn", "Workers linger after shutdown. " + OUTPUT_MARKER);
        server.respondInOrder(status(200, answer("stop", output.toString())));

        grader(LIMITS).grade(TITLE, BODY, LABELS);

        assertThat(logs).contains("이슈 판정 응답 model=" + ANSWERED_MODEL
                + " finishReason=stop input=1830 output=412 cached=1664");
        assertThat(logs).doesNotContain(OUTPUT_MARKER).doesNotContain(ISSUE_BODY_MARKER);
    }

    private GptIssueGrader grader(LlmCallLimits limits) {
        return grader(limits, DEFAULT_TEMPERATURE);
    }

    private GptIssueGrader grader(LlmCallLimits limits, String temperature) {
        return new GptIssueGrader(client(limits), REQUESTED_MODEL, temperature);
    }

    private OpenAIClient client(LlmCallLimits limits) {
        OpenAIClient client = server.openAiClient(limits);
        clients.add(client);
        return client;
    }

    private static ObjectNode validOutput() {
        return JSON.createObjectNode()
                .put("evidenceCause", "ABSENT")
                .put("evidenceFixDirection", "PARTIAL")
                .put("evidenceProblem", "PRESENT")
                .put("evidenceRelatedPr", true)
                .put("evidenceReproduction", "PRESENT")
                .put("exclusion", "NONE")
                .put("level", "MEDIUM")
                .put("reasonEn", "Steps are given but the cause is missing.")
                .put("reasonKo", "재현 절차는 있지만 원인이 없다.")
                .put("summaryEn", "Workers linger because of the shutdown hook order.")
                .put("summaryKo", "종료 훅 순서 때문에 워커가 남는다.");
    }

    private static String without(String field) {
        ObjectNode output = validOutput();
        output.remove(field);
        return output.toString();
    }

    private static String answer(String finishReason, String content) {
        return answerNode(finishReason, content).toString();
    }

    private static ObjectNode answerNode(String finishReason, String content) {
        ObjectNode answer = envelope();
        ObjectNode message = choice(answer, finishReason);
        message.put("content", content);
        message.putNull("refusal");
        return answer;
    }

    private static String answerWithout(String field) {
        ObjectNode answer = answerNode("stop", validOutput().toString());
        answer.remove(field);
        return answer.toString();
    }

    private static String answerWithoutContent(String finishReason) {
        ObjectNode answer = envelope();
        ObjectNode message = choice(answer, finishReason);
        message.putNull("content");
        message.putNull("refusal");
        return answer.toString();
    }

    private static String refusal(String refusal) {
        ObjectNode answer = envelope();
        ObjectNode message = choice(answer, "stop");
        message.putNull("content");
        message.put("refusal", refusal);
        return answer.toString();
    }

    private static String answerWithoutChoices() {
        ObjectNode answer = envelope();
        answer.putArray("choices");
        return answer.toString();
    }

    private static ObjectNode choice(ObjectNode answer, String finishReason) {
        ObjectNode choice = answer.putArray("choices").addObject()
                .put("index", 0)
                .put("finish_reason", finishReason);
        choice.putNull("logprobs");
        return choice.putObject("message").put("role", "assistant");
    }

    private static ObjectNode envelope() {
        ObjectNode answer = JSON.createObjectNode()
                .put("id", "chatcmpl-test")
                .put("object", "chat.completion")
                .put("created", 1_700_000_000L)
                .put("model", ANSWERED_MODEL)
                .put("system_fingerprint", "fp_test");
        ObjectNode usage = answer.putObject("usage")
                .put("prompt_tokens", 1_830)
                .put("completion_tokens", 412)
                .put("total_tokens", 2_242);
        usage.putObject("prompt_tokens_details").put("cached_tokens", 1_664);
        return answer;
    }

    private static String errorBody(String errorType, String message) {
        ObjectNode body = JSON.createObjectNode();
        body.putObject("error")
                .put("message", message)
                .put("type", errorType)
                .put("param", OTHER_FIELD_MARKER)
                .put("code", OTHER_FIELD_MARKER);
        return body.toString();
    }

    private static JsonNode outputSchema() {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(REQUESTED_MODEL)
                .addUserMessage("schema")
                .responseFormat(IssueGradingOutput.class)
                .build()
                .rawParams();
        return ObjectMappers.jsonMapper().valueToTree(params.responseFormat().orElseThrow()).at("/json_schema/schema");
    }

    private static List<String> textsOf(Throwable failure) {
        List<String> texts = new ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            texts.add(current.toString());
            Arrays.stream(current.getSuppressed()).map(Throwable::toString).forEach(texts::add);
        }
        return texts;
    }

    private static Duration worstCase(LlmCallLimits limits) {
        return limits.callTimeout().multipliedBy(limits.maxRetries() + 1L)
                .plus(limits.maxRetryWait().multipliedBy(limits.maxRetries()));
    }
}
