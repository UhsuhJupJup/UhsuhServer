package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.client.AnthropicClient;
import com.openai.client.OpenAIClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import uhsuhjupjup.backend.config.llm.LlmCallLimits;
import uhsuhjupjup.backend.config.llm.MockLlmServer;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.stall;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.status;

@ExtendWith(OutputCaptureExtension.class)
class ResilientIssueGraderTest {

    private static final String TITLE = "Retry interval is ignored";
    private static final String BODY = "Steps are below.";
    private static final List<String> LABELS = List.of("bug");
    private static final int MINIMUM_NUMBER_OF_CALLS = 10;
    private static final String RAW_MARKER = "RAWTEXTMARKER";
    private static final String ISSUE_BODY_MARKER = "ISSUEBODYMARKER";
    private static final String OUTPUT_MARKER = "OUTPUTMARKER";
    private static final LlmCallLimits LIMITS =
            new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(5), 1, Duration.ofSeconds(2));
    private static final OssIssueVerdict VERDICT = new OssIssueVerdict(OssIssueDifficulty.EASY,
            OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT,
            false, null, "모두 있다.", "Everything is given.", "설정이 무시된다.", "The setting is ignored.");
    private static final IssueGradingResult CLAUDE_RESULT =
            new IssueGradingResult(VERDICT, "claude-haiku-4-5-20251001");
    private static final IssueGradingResult GPT_RESULT = new IssueGradingResult(VERDICT, "gpt-4o-mini-2024-07-18");

    private final ClaudeIssueGrader claude = mock(ClaudeIssueGrader.class);
    private final GptIssueGrader gpt = mock(GptIssueGrader.class);
    private final CircuitBreakerRegistry circuits = CircuitBreakerRegistry.of(circuitConfig());

    @Test
    void claudeAnswers_gptIsNeverCalled() {
        willReturn(CLAUDE_RESULT).given(claude).grade(TITLE, BODY, LABELS);

        IssueGradingResult result = grader(claude, gpt).grade(TITLE, BODY, LABELS);

        assertThat(result).isSameAs(CLAUDE_RESULT);
        verifyNoInteractions(gpt);
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void claudeFailsForAnyReason_gptGradesTheIssue(Reason reason, CapturedOutput logs) {
        willThrow(new IssueGradingException(reason, "Claude 실패 설명")).given(claude).grade(TITLE, BODY, LABELS);
        willReturn(GPT_RESULT).given(gpt).grade(TITLE, BODY, LABELS);

        IssueGradingResult result = grader(claude, gpt).grade(TITLE, BODY, LABELS);

        assertThat(result).isSameAs(GPT_RESULT);
        assertThat(logs).contains("Claude 이슈 판정 실패 reason=" + reason + " Claude 실패 설명");
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"UNAVAILABLE", "REJECTED"})
    void claudeFailingEveryCall_opensItsCircuitAndLaterIssuesGoStraightToGpt(Reason reason) {
        willThrow(new IssueGradingException(reason, "Claude 실패")).given(claude).grade(TITLE, BODY, LABELS);
        willReturn(GPT_RESULT).given(gpt).grade(TITLE, BODY, LABELS);
        ResilientIssueGrader grader = grader(claude, gpt);

        repeat(MINIMUM_NUMBER_OF_CALLS, () -> grader.grade(TITLE, BODY, LABELS));
        IssueGradingResult result = grader.grade(TITLE, BODY, LABELS);

        assertThat(result).isSameAs(GPT_RESULT);
        assertThat(circuit(ResilientIssueGrader.CLAUDE_CIRCUIT).getState()).isEqualTo(State.OPEN);
        verify(claude, times(MINIMUM_NUMBER_OF_CALLS)).grade(TITLE, BODY, LABELS);
        verify(gpt, times(MINIMUM_NUMBER_OF_CALLS + 1)).grade(TITLE, BODY, LABELS);
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"REFUSED", "TRUNCATED", "INVALID_OUTPUT", "INVALID_INPUT"})
    void failuresOfOneIssue_neverOpenTheCircuitAndAreNotCountedAsSuccesses(Reason reason) {
        willThrow(new IssueGradingException(reason, "이 이슈만 실패")).given(claude).grade(TITLE, BODY, LABELS);
        willReturn(GPT_RESULT).given(gpt).grade(TITLE, BODY, LABELS);
        ResilientIssueGrader grader = grader(claude, gpt);

        repeat(30, () -> grader.grade(TITLE, BODY, LABELS));

        CircuitBreaker claudeCircuit = circuit(ResilientIssueGrader.CLAUDE_CIRCUIT);
        assertThat(claudeCircuit.getState()).isEqualTo(State.CLOSED);
        assertThat(claudeCircuit.getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(claudeCircuit.getMetrics().getNumberOfSuccessfulCalls()).isZero();
        assertThat(claudeCircuit.getMetrics().getNumberOfFailedCalls()).isZero();
        verify(claude, times(30)).grade(TITLE, BODY, LABELS);
    }

    @Test
    void failuresOfOneIssue_doNotDiluteTheFailureRateOfFailuresThatHitEveryCall() {
        IssueGradingException[] twoRefusalsPerOutage = Stream.generate(() -> Stream.of(
                        new IssueGradingException(Reason.REFUSED, "거절"),
                        new IssueGradingException(Reason.REFUSED, "거절"),
                        new IssueGradingException(Reason.UNAVAILABLE, "장애")))
                .limit(MINIMUM_NUMBER_OF_CALLS)
                .flatMap(failures -> failures)
                .toArray(IssueGradingException[]::new);
        willThrow(twoRefusalsPerOutage).given(claude).grade(TITLE, BODY, LABELS);
        willReturn(GPT_RESULT).given(gpt).grade(TITLE, BODY, LABELS);
        ResilientIssueGrader grader = grader(claude, gpt);

        repeat(twoRefusalsPerOutage.length, () -> grader.grade(TITLE, BODY, LABELS));

        CircuitBreaker claudeCircuit = circuit(ResilientIssueGrader.CLAUDE_CIRCUIT);
        assertThat(claudeCircuit.getState()).isEqualTo(State.OPEN);
        assertThat(claudeCircuit.getMetrics().getNumberOfSuccessfulCalls()).isZero();
    }

    @Test
    void gptCircuit_countsFailuresTheSameWay() {
        ResilientIssueGrader grader = grader(null, gpt);
        willThrow(new IssueGradingException(Reason.INVALID_OUTPUT, "출력 위반")).given(gpt).grade(TITLE, BODY, LABELS);

        repeat(30, () -> catchThrowable(() -> grader.grade(TITLE, BODY, LABELS)));
        State afterFailuresOfOneIssue = circuit(ResilientIssueGrader.GPT_CIRCUIT).getState();
        willThrow(new IssueGradingException(Reason.UNAVAILABLE, "장애")).given(gpt).grade(TITLE, BODY, LABELS);
        repeat(MINIMUM_NUMBER_OF_CALLS, () -> catchThrowable(() -> grader.grade(TITLE, BODY, LABELS)));
        Throwable failure = catchThrowable(() -> grader.grade(TITLE, BODY, LABELS));

        assertThat(afterFailuresOfOneIssue).isEqualTo(State.CLOSED);
        assertThat(circuit(ResilientIssueGrader.GPT_CIRCUIT).getState()).isEqualTo(State.OPEN);
        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
            assertThat(e.getMessage()).isEqualTo("이슈를 판정하지 못했습니다: Claude 꺼짐, "
                    + "GPT UNAVAILABLE(GPT 판정 서킷이 열려 있어 부르지 않았습니다)");
        });
        verify(gpt, times(30 + MINIMUM_NUMBER_OF_CALLS)).grade(TITLE, BODY, LABELS);
    }

    @Test
    void openCircuits_areSkippedAndReportedAsUnavailable() {
        circuit(ResilientIssueGrader.CLAUDE_CIRCUIT).transitionToOpenState();
        circuit(ResilientIssueGrader.GPT_CIRCUIT).transitionToOpenState();

        Throwable failure = catchThrowable(() -> grader(claude, gpt).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
            assertThat(e.getMessage()).isEqualTo("이슈를 판정하지 못했습니다: "
                    + "Claude UNAVAILABLE(Claude 판정 서킷이 열려 있어 부르지 않았습니다), "
                    + "GPT UNAVAILABLE(GPT 판정 서킷이 열려 있어 부르지 않았습니다)");
        });
        verifyNoInteractions(claude, gpt);
    }

    @Test
    void openingACircuit_isWarnedOnceAndSkippedCallsAreOnlyDebugLogged() {
        Logger logger = (Logger) LoggerFactory.getLogger(ResilientIssueGrader.class);
        Level levelBefore = logger.getLevel();
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        logger.setLevel(Level.DEBUG);
        try {
            willThrow(new IssueGradingException(Reason.UNAVAILABLE, "장애")).given(claude).grade(TITLE, BODY, LABELS);
            willReturn(GPT_RESULT).given(gpt).grade(TITLE, BODY, LABELS);
            ResilientIssueGrader grader = grader(claude, gpt);

            repeat(MINIMUM_NUMBER_OF_CALLS + 5, () -> grader.grade(TITLE, BODY, LABELS));

            List<String> warnings = messagesAt(events, Level.WARN);
            assertThat(warnings).containsOnlyOnce("이슈 판정 서킷 상태 변경 circuit=issueGrader from=CLOSED to=OPEN");
            assertThat(warnings).filteredOn(message -> message.startsWith("Claude 이슈 판정 실패"))
                    .hasSize(MINIMUM_NUMBER_OF_CALLS);
            assertThat(warnings).noneMatch(message -> message.contains("서킷이 열려 있어"));
            assertThat(messagesAt(events, Level.DEBUG)).containsExactly(
                    Collections.nCopies(5, "Claude 판정 서킷이 열려 있어 부르지 않음").toArray(String[]::new));
        } finally {
            logger.detachAppender(events);
            logger.setLevel(levelBefore);
        }
    }

    @Test
    void errorsInHalfOpen_holdTheTrialPermitsOnlyUntilTheMaxWaitSendsTheCircuitBackToOpen() {
        CircuitBreakerRegistry shortWait = CircuitBreakerRegistry.of(CircuitBreakerConfig.from(circuitConfig())
                .maxWaitDurationInHalfOpenState(Duration.ofMillis(300))
                .build());
        CircuitBreaker claudeCircuit = shortWait.circuitBreaker(ResilientIssueGrader.CLAUDE_CIRCUIT);
        willThrow(new AssertionError("판정기 안의 Error")).given(claude).grade(TITLE, BODY, LABELS);
        willReturn(GPT_RESULT).given(gpt).grade(TITLE, BODY, LABELS);
        ResilientIssueGrader grader = new ResilientIssueGrader(shortWait,
                providerOf(ClaudeIssueGrader.class, claude), providerOf(GptIssueGrader.class, gpt));
        claudeCircuit.transitionToOpenState();
        claudeCircuit.transitionToHalfOpenState();

        repeat(3, () -> catchThrowable(() -> grader.grade(TITLE, BODY, LABELS)));
        IssueGradingResult whileThePermitsAreHeld = grader.grade(TITLE, BODY, LABELS);

        assertThat(whileThePermitsAreHeld).isSameAs(GPT_RESULT);
        verify(claude, times(3)).grade(TITLE, BODY, LABELS);
        await().atMost(Duration.ofSeconds(3)).until(() -> claudeCircuit.getState() == State.OPEN);
    }

    @Test
    void interruptedWhileClaudeFails_gptIsNotCalledAndNeitherCircuitCountsIt() {
        willAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new IssueGradingException(Reason.UNAVAILABLE, "Claude 판정 호출을 그만뒀습니다: 재시도를 기다리다 중단됐습니다");
        }).given(claude).grade(TITLE, BODY, LABELS);
        try {
            Throwable failure = catchThrowable(() -> grader(claude, gpt).grade(TITLE, BODY, LABELS));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
                assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                assertThat(e.getMessage()).isEqualTo("이슈 판정이 중단됐습니다: "
                        + "Claude UNAVAILABLE(Claude 판정 중에 스레드가 중단됐습니다), GPT 부르지 않음");
            });
            verifyNoInteractions(gpt);
            assertNotCounted(ResilientIssueGrader.CLAUDE_CIRCUIT);
            assertNotCounted(ResilientIssueGrader.GPT_CIRCUIT);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedWhileGptFails_isReportedAsUnavailableAndNotCounted() {
        IssueGradingException refused = new IssueGradingException(Reason.REFUSED, "Claude가 판정을 거절했습니다");
        willThrow(refused).given(claude).grade(TITLE, BODY, LABELS);
        willAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new IssueGradingException(Reason.UNAVAILABLE, "GPT 판정 호출이 실패했습니다(OpenAIIoException)");
        }).given(gpt).grade(TITLE, BODY, LABELS);
        try {
            Throwable failure = catchThrowable(() -> grader(claude, gpt).grade(TITLE, BODY, LABELS));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
                assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                assertThat(e.getMessage()).isEqualTo("이슈 판정이 중단됐습니다: "
                        + "Claude REFUSED(Claude가 판정을 거절했습니다), "
                        + "GPT UNAVAILABLE(GPT 판정 중에 스레드가 중단됐습니다)");
                assertThat(e.getCause()).isSameAs(refused);
            });
            assertNotCounted(ResilientIssueGrader.GPT_CIRCUIT);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void alreadyInterrupted_callsNoGrader() {
        Thread.currentThread().interrupt();
        try {
            Throwable failure = catchThrowable(() -> grader(claude, gpt).grade(TITLE, BODY, LABELS));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
                assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                assertThat(e.getMessage()).isEqualTo("이슈 판정이 중단됐습니다: Claude 부르지 않음, GPT 부르지 않음");
                assertThat(e).hasNoCause();
            });
            verifyNoInteractions(claude, gpt);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptDuringARealClaudeCall_stopsBeforeGptAndKeepsTheFlag() throws Exception {
        MockLlmServer claudeServer = new MockLlmServer();
        MockLlmServer gptServer = new MockLlmServer();
        LlmCallLimits oneSecondCall =
                new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(2));
        AnthropicClient anthropicClient = claudeServer.anthropicClient(oneSecondCall);
        OpenAIClient openAiClient = gptServer.openAiClient(LIMITS);
        ScheduledExecutorService interrupter = Executors.newSingleThreadScheduledExecutor();
        Thread gradingThread = Thread.currentThread();
        try {
            claudeServer.respondInOrder(stall());
            ResilientIssueGrader grader = grader(
                    new ClaudeIssueGrader(anthropicClient, "claude-haiku-4-5", "0"),
                    new GptIssueGrader(openAiClient, "gpt-4o-mini", "0"));
            interrupter.schedule(gradingThread::interrupt, 200, TimeUnit.MILLISECONDS);

            Throwable failure = catchThrowable(() -> grader.grade(TITLE, BODY, LABELS));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
                assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                assertThat(e.getMessage()).startsWith("이슈 판정이 중단됐습니다: Claude UNAVAILABLE(")
                        .endsWith(", GPT 부르지 않음");
                assertThat(e.getCause()).isInstanceOf(GradingInterruptedException.class);
            });
            assertThat(gptServer.requestCount()).isZero();
            assertNotCounted(ResilientIssueGrader.CLAUDE_CIRCUIT);
        } finally {
            Thread.interrupted();
            interrupter.shutdownNow();
            anthropicClient.close();
            openAiClient.close();
            claudeServer.stop();
            gptServer.stop();
        }
    }

    @Test
    void gptOff_claudeFailureIsReportedWithItsOwnReason() {
        IssueGradingException claudeFailure =
                new IssueGradingException(Reason.REFUSED, "Claude가 판정을 거절했습니다(stop_reason=refusal)");
        willThrow(claudeFailure).given(claude).grade(TITLE, BODY, LABELS);

        Throwable failure = catchThrowable(() -> grader(claude, null).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.REFUSED);
            assertThat(e.getMessage()).isEqualTo("이슈를 판정하지 못했습니다: "
                    + "Claude REFUSED(Claude가 판정을 거절했습니다(stop_reason=refusal)), GPT 꺼짐");
            assertThat(e.getCause()).isSameAs(claudeFailure);
            assertThat(e.getSuppressed()).isEmpty();
        });
    }

    @Test
    void bothOff_failsAsUnavailableWithoutCallingAnyGrader() {
        Throwable failure = catchThrowable(() -> grader(null, null).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
            assertThat(e.getMessage()).isEqualTo("이슈를 판정하지 못했습니다: Claude 꺼짐, GPT 꺼짐");
            assertThat(e).hasNoCause();
        });
    }

    @Test
    void claudeOff_gptGradesAndTheClaudeCircuitIsLeftAlone() {
        willReturn(GPT_RESULT).given(gpt).grade(TITLE, BODY, LABELS);

        IssueGradingResult result = grader(null, gpt).grade(TITLE, BODY, LABELS);

        assertThat(result).isSameAs(GPT_RESULT);
        assertThat(circuit(ResilientIssueGrader.CLAUDE_CIRCUIT).getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(circuit(ResilientIssueGrader.GPT_CIRCUIT).getMetrics().getNumberOfSuccessfulCalls()).isOne();
    }

    @ParameterizedTest(name = "Claude {0}, GPT {1} -> {2}({3})")
    @CsvSource({
            "REFUSED, UNAVAILABLE, REFUSED, Claude",
            "UNAVAILABLE, REFUSED, REFUSED, GPT",
            "REJECTED, TRUNCATED, TRUNCATED, GPT",
            "INVALID_OUTPUT, REJECTED, INVALID_OUTPUT, Claude",
            "INVALID_INPUT, UNAVAILABLE, INVALID_INPUT, Claude",
            "TRUNCATED, INVALID_OUTPUT, INVALID_OUTPUT, GPT",
            "REFUSED, REFUSED, REFUSED, GPT",
            "REJECTED, UNAVAILABLE, UNAVAILABLE, GPT",
            "UNAVAILABLE, REJECTED, REJECTED, GPT"})
    void bothFail_theReasonPrefersTheLastFailureOfTheIssueItself(Reason claudeReason, Reason gptReason,
                                                                  Reason reason, String decisiveGrader) {
        IssueGradingException claudeFailure = new IssueGradingException(claudeReason, "Claude 실패");
        IssueGradingException gptFailure = new IssueGradingException(gptReason, "GPT 실패");
        willThrow(claudeFailure).given(claude).grade(TITLE, BODY, LABELS);
        willThrow(gptFailure).given(gpt).grade(TITLE, BODY, LABELS);
        IssueGradingException decisive = decisiveGrader.equals("Claude") ? claudeFailure : gptFailure;
        IssueGradingException other = decisive == claudeFailure ? gptFailure : claudeFailure;

        Throwable failure = catchThrowable(() -> grader(claude, gpt).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(reason);
            assertThat(e.getMessage()).isEqualTo("이슈를 판정하지 못했습니다: Claude " + claudeReason
                    + "(Claude 실패), GPT " + gptReason + "(GPT 실패)");
            assertThat(e.getCause()).isSameAs(decisive);
            assertThat(e.getSuppressed()).containsExactly(other);
        });
    }

    @Test
    void unexpectedFailureOfAGrader_isCountedAsAnOutageWithoutItsText(CapturedOutput logs) {
        willThrow(new IllegalStateException("leaked " + RAW_MARKER)).given(claude).grade(TITLE, BODY, LABELS);
        willThrow(new IllegalArgumentException("leaked " + RAW_MARKER)).given(gpt).grade(TITLE, BODY, LABELS);

        Throwable failure = catchThrowable(() -> grader(claude, gpt).grade(TITLE, BODY, LABELS));

        assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
            assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
            assertThat(e.getMessage()).contains(
                    "Claude 판정기가 예상 밖 예외를 던졌습니다(IllegalStateException)",
                    "GPT 판정기가 예상 밖 예외를 던졌습니다(IllegalArgumentException)");
        });
        assertThat(textsOf(failure)).noneMatch(text -> text.contains(RAW_MARKER));
        assertThat(logs).doesNotContain(RAW_MARKER);
        assertThat(circuit(ResilientIssueGrader.CLAUDE_CIRCUIT).getMetrics().getNumberOfFailedCalls()).isOne();
        assertThat(circuit(ResilientIssueGrader.GPT_CIRCUIT).getMetrics().getNumberOfFailedCalls()).isOne();
    }

    @Test
    void realGraders_keepTheIssueAndTheOutputsOutOfTheFailureAndTheLogs(CapturedOutput logs) throws Exception {
        MockLlmServer claudeServer = new MockLlmServer();
        MockLlmServer gptServer = new MockLlmServer();
        AnthropicClient anthropicClient = claudeServer.anthropicClient(LIMITS);
        OpenAIClient openAiClient = gptServer.openAiClient(LIMITS);
        try {
            claudeServer.respondInOrder(status(200, claudeRefusal("I can't grade this. " + OUTPUT_MARKER)));
            gptServer.respondInOrder(status(200, gptAnswer("{\"level\": \"EASY\", \"reasonEn\": \"" + OUTPUT_MARKER)));
            ResilientIssueGrader grader = grader(
                    new ClaudeIssueGrader(anthropicClient, "claude-haiku-4-5", "0"),
                    new GptIssueGrader(openAiClient, "gpt-4o-mini", "0"));

            Throwable failure = catchThrowable(() -> grader.grade(TITLE, BODY + " " + ISSUE_BODY_MARKER, LABELS));

            assertThat(failure).isInstanceOfSatisfying(IssueGradingException.class, e -> {
                assertThat(e.getReason()).isEqualTo(Reason.INVALID_OUTPUT);
                assertThat(e.getMessage()).startsWith("이슈를 판정하지 못했습니다: Claude REFUSED(")
                        .contains(", GPT INVALID_OUTPUT(");
            });
            assertThat(textsOf(failure))
                    .noneMatch(text -> text.contains(OUTPUT_MARKER) || text.contains(ISSUE_BODY_MARKER));
            assertThat(logs).contains("Claude 이슈 판정 실패 reason=REFUSED", "GPT 이슈 판정 실패 reason=INVALID_OUTPUT")
                    .doesNotContain(OUTPUT_MARKER)
                    .doesNotContain(ISSUE_BODY_MARKER);
        } finally {
            anthropicClient.close();
            openAiClient.close();
            claudeServer.stop();
            gptServer.stop();
        }
    }

    private ResilientIssueGrader grader(ClaudeIssueGrader claudeGrader, GptIssueGrader gptGrader) {
        return new ResilientIssueGrader(circuits,
                providerOf(ClaudeIssueGrader.class, claudeGrader),
                providerOf(GptIssueGrader.class, gptGrader));
    }

    private CircuitBreaker circuit(String name) {
        return circuits.circuitBreaker(name);
    }

    private void assertNotCounted(String circuitName) {
        CircuitBreaker.Metrics metrics = circuit(circuitName).getMetrics();
        assertThat(metrics.getNumberOfBufferedCalls()).isZero();
        assertThat(metrics.getNumberOfFailedCalls()).isZero();
        assertThat(metrics.getNumberOfSuccessfulCalls()).isZero();
    }

    private static List<String> messagesAt(ListAppender<ILoggingEvent> events, Level level) {
        return events.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static <T> ObjectProvider<T> providerOf(Class<T> type, T bean) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (bean != null) {
            beans.addBean(type.getSimpleName(), bean);
        }
        return beans.getBeanProvider(type);
    }

    private static CircuitBreakerConfig circuitConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(MINIMUM_NUMBER_OF_CALLS)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordException(new SystemicGradingFailure())
                .ignoreException(new IssueSpecificGradingFailure())
                .ignoreExceptions(GradingInterruptedException.class)
                .build();
    }

    private static void repeat(int times, Runnable call) {
        IntStream.range(0, times).forEach(index -> call.run());
    }

    private static String claudeRefusal(String text) {
        return """
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-haiku-4-5-20251001",
                 "content": [{"type": "text", "text": "%s"}], "stop_reason": "refusal", "stop_sequence": null,
                 "usage": {"input_tokens": 10, "output_tokens": 5}}
                """.formatted(text);
    }

    private static String gptAnswer(String content) {
        return """
                {"id": "chatcmpl-test", "object": "chat.completion", "created": 1700000000,
                 "model": "gpt-4o-mini-2024-07-18",
                 "choices": [{"index": 0, "finish_reason": "stop", "logprobs": null,
                              "message": {"role": "assistant", "content": %s, "refusal": null}}],
                 "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}}
                """.formatted(quoted(content));
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static List<String> textsOf(Throwable failure) {
        List<String> texts = new ArrayList<>();
        collectTexts(failure, texts);
        return texts;
    }

    private static void collectTexts(Throwable failure, List<String> texts) {
        if (failure == null) {
            return;
        }
        texts.add(failure.toString());
        for (Throwable suppressed : failure.getSuppressed()) {
            collectTexts(suppressed, texts);
        }
        collectTexts(failure.getCause(), texts);
    }
}
