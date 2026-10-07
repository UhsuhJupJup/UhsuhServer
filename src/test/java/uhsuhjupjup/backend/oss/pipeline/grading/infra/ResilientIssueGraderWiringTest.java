package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.anthropic.client.AnthropicClient;
import com.openai.client.OpenAIClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import uhsuhjupjup.backend.config.llm.LlmCallLimits;
import uhsuhjupjup.backend.config.llm.MockLlmServer;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGrader;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.status;

class ResilientIssueGraderWiringTest {

    private static final LlmCallLimits LIMITS =
            new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(5), 1, Duration.ofSeconds(2));
    private static final String CLAUDE_ANSWERED_MODEL = "claude-haiku-4-5-20251001";
    private static final String GPT_ANSWERED_MODEL = "gpt-4o-mini-2024-07-18";
    private static final String VALID_OUTPUT = """
            {"evidenceCause": "ABSENT", "evidenceFixDirection": "PARTIAL", "evidenceProblem": "PRESENT",
             "evidenceRelatedPr": true, "evidenceReproduction": "PRESENT", "exclusion": "NONE", "level": "MEDIUM",
             "reasonEn": "Steps are given but the cause is missing.", "reasonKo": "재현 절차는 있지만 원인이 없다.",
             "summaryEn": "Workers linger after shutdown.", "summaryKo": "종료 뒤 워커가 남는다."}
            """;
    private static final String CLAUDE_BAD_REQUEST = """
            {"type": "error", "error": {"type": "invalid_request_error", "message": "bad request"}}
            """;

    private final List<AnthropicClient> anthropicClients = new ArrayList<>();
    private final List<OpenAIClient> openAiClients = new ArrayList<>();
    private MockLlmServer claudeServer;
    private MockLlmServer gptServer;

    @BeforeEach
    void setUp() throws IOException {
        claudeServer = new MockLlmServer();
        gptServer = new MockLlmServer();
    }

    @AfterEach
    void tearDown() {
        anthropicClients.forEach(AnthropicClient::close);
        openAiClients.forEach(OpenAIClient::close);
        claudeServer.stop();
        gptServer.stop();
    }

    @ParameterizedTest(name = "Claude {0}, GPT {1}")
    @CsvSource({"false, false", "true, false", "false, true", "true, true"})
    void everyFlagCombination_startsAndInjectsTheResilientGrader(boolean claudeEnabled, boolean gptEnabled) {
        runner(claudeEnabled, gptEnabled).withBean(GradingStep.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GradingStep.class).grader).isInstanceOf(ResilientIssueGrader.class);
            assertThat(context.getBeansOfType(ClaudeIssueGrader.class)).hasSize(claudeEnabled ? 1 : 0);
            assertThat(context.getBeansOfType(GptIssueGrader.class)).hasSize(gptEnabled ? 1 : 0);
        });
    }

    @Test
    void bothOff_failsAsUnavailableWithoutCallingAnyApi() {
        runner(false, false).run(context -> {
            IssueGrader grader = context.getBean(IssueGrader.class);

            Throwable failure = catchThrowable(() -> grader.grade("Title", "Body", List.of()));

            assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
                assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                assertThat(e.getMessage()).isEqualTo("이슈를 판정하지 못했습니다: Claude 꺼짐, GPT 꺼짐");
            });
        });
        assertThat(claudeServer.requestCount()).isZero();
        assertThat(gptServer.requestCount()).isZero();
    }

    @Test
    void claudeOnly_gradesWithClaude() {
        claudeServer.respondInOrder(status(200, claudeAnswer()));

        runner(true, false).run(context -> {
            IssueGradingResult result = context.getBean(IssueGrader.class).grade("Title", "Body", List.of());

            assertThat(result.model()).isEqualTo(CLAUDE_ANSWERED_MODEL);
        });
        assertThat(gptServer.requestCount()).isZero();
    }

    @Test
    void gptOnly_gradesWithGptWithoutCallingClaude() {
        gptServer.respondInOrder(status(200, gptAnswer()));

        runner(false, true).run(context -> {
            IssueGradingResult result = context.getBean(IssueGrader.class).grade("Title", "Body", List.of());

            assertThat(result.model()).isEqualTo(GPT_ANSWERED_MODEL);
        });
        assertThat(claudeServer.requestCount()).isZero();
    }

    @Test
    void both_fallsBackToGptWhenClaudeFails() {
        claudeServer.respondInOrder(status(400, CLAUDE_BAD_REQUEST));
        gptServer.respondInOrder(status(200, gptAnswer()));

        runner(true, true).run(context -> {
            IssueGradingResult result = context.getBean(IssueGrader.class).grade("Title", "Body", List.of());

            assertThat(result.model()).isEqualTo(GPT_ANSWERED_MODEL);
        });
        assertThat(claudeServer.requestCount()).isOne();
        assertThat(gptServer.requestCount()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {ResilientIssueGrader.CLAUDE_CIRCUIT, ResilientIssueGrader.GPT_CIRCUIT})
    void circuitSettings_followTheDecidedValuesAndSortFailuresByScope(String name) {
        runner(true, true).run(context -> {
            CircuitBreakerConfig config = context.getBean(CircuitBreakerRegistry.class)
                    .circuitBreaker(name)
                    .getCircuitBreakerConfig();

            assertThat(config.getSlidingWindowType()).isEqualTo(SlidingWindowType.COUNT_BASED);
            assertThat(config.getSlidingWindowSize()).isEqualTo(20);
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
            assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
            assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(30_000L);
            assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
            assertThat(config.getMaxWaitDurationInHalfOpenState()).isEqualTo(Duration.ofMinutes(2));
            assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
            assertThat(config.getRecordExceptionPredicate()).isInstanceOf(SystemicGradingFailure.class);
            Predicate<Throwable> ignored = config.getIgnoreExceptionPredicate();
            assertThat(Stream.of(Reason.REFUSED, Reason.TRUNCATED, Reason.INVALID_OUTPUT, Reason.INVALID_INPUT))
                    .allMatch(reason -> ignored.test(new IssueGradingException(reason, "이 이슈만 실패")));
            assertThat(Stream.of(Reason.UNAVAILABLE, Reason.REJECTED))
                    .noneMatch(reason -> ignored.test(new IssueGradingException(reason, "모든 호출 실패")));
            assertThat(ignored.test(new GradingInterruptedException("판정 중에 스레드가 중단됐습니다",
                    new IssueGradingException(Reason.UNAVAILABLE, "재시도를 기다리다 중단됐습니다")))).isTrue();
        });
    }

    @Test
    void claudeCircuitFromTheSettings_opensOnlyOnFailuresThatHitEveryCall() {
        gptServer.respondInOrder(status(200, gptAnswer()));

        runner(true, true).run(context -> {
            IssueGrader grader = context.getBean(IssueGrader.class);
            CircuitBreaker claudeCircuit = context.getBean(CircuitBreakerRegistry.class)
                    .circuitBreaker(ResilientIssueGrader.CLAUDE_CIRCUIT);

            claudeServer.respondInOrder(status(200, claudeRefusal()));
            IntStream.range(0, 15).forEach(index -> grader.grade("Title", "Body", List.of()));
            State afterRefusals = claudeCircuit.getState();
            int bufferedAfterRefusals = claudeCircuit.getMetrics().getNumberOfBufferedCalls();
            claudeServer.respondInOrder(status(400, CLAUDE_BAD_REQUEST));
            IntStream.range(0, 10).forEach(index -> grader.grade("Title", "Body", List.of()));
            int claudeRequestsWhenOpened = claudeServer.requestCount();
            IssueGradingResult result = grader.grade("Title", "Body", List.of());

            assertThat(afterRefusals).isEqualTo(State.CLOSED);
            assertThat(bufferedAfterRefusals).isZero();
            assertThat(claudeCircuit.getState()).isEqualTo(State.OPEN);
            assertThat(claudeServer.requestCount()).isEqualTo(claudeRequestsWhenOpened).isEqualTo(25);
            assertThat(result.model()).isEqualTo(GPT_ANSWERED_MODEL);
        });
    }

    private ApplicationContextRunner runner(boolean claudeEnabled, boolean gptEnabled) {
        Map<String, Object> env = Map.of(
                "OSS_GRADING_CLAUDE_ENABLED", String.valueOf(claudeEnabled),
                "OSS_GRADING_GPT_ENABLED", String.valueOf(gptEnabled));
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class))
                .withBean(IssueGraderAnthropicConfig.ISSUE_GRADER_CLIENT, AnthropicClient.class, this::anthropicClient)
                .withBean(IssueGraderOpenAiConfig.ISSUE_GRADER_CLIENT, OpenAIClient.class, this::openAiClient)
                .withConfiguration(UserConfigurations.of(
                        ClaudeIssueGrader.class, GptIssueGrader.class, ResilientIssueGrader.class));
    }

    private AnthropicClient anthropicClient() {
        AnthropicClient client = claudeServer.anthropicClient(LIMITS);
        anthropicClients.add(client);
        return client;
    }

    private OpenAIClient openAiClient() {
        OpenAIClient client = gptServer.openAiClient(LIMITS);
        openAiClients.add(client);
        return client;
    }

    private static String claudeAnswer() {
        return """
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "%s",
                 "content": [{"type": "text", "text": %s}], "stop_reason": "end_turn", "stop_sequence": null,
                 "usage": {"input_tokens": 10, "output_tokens": 5}}
                """.formatted(CLAUDE_ANSWERED_MODEL, quoted(VALID_OUTPUT));
    }

    private static String claudeRefusal() {
        return """
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "%s",
                 "content": [{"type": "text", "text": "I can't grade this."}], "stop_reason": "refusal",
                 "stop_sequence": null, "usage": {"input_tokens": 10, "output_tokens": 5}}
                """.formatted(CLAUDE_ANSWERED_MODEL);
    }

    private static String gptAnswer() {
        return """
                {"id": "chatcmpl-test", "object": "chat.completion", "created": 1700000000, "model": "%s",
                 "choices": [{"index": 0, "finish_reason": "stop", "logprobs": null,
                              "message": {"role": "assistant", "content": %s, "refusal": null}}],
                 "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}}
                """.formatted(GPT_ANSWERED_MODEL, quoted(VALID_OUTPUT));
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static class GradingStep {

        private final IssueGrader grader;

        GradingStep(IssueGrader grader) {
            this.grader = grader;
        }
    }
}
