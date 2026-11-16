package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisClaim;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisJobOutcome;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJobStatus;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisReason;
import uhsuhjupjup.backend.learningnote.infra.NoteAnalysisJobRepository;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NoteAnalysisJobRunnerTest {

    private static final int RETRY_PERMITS = 2;
    private static final int REQUEST_PERMITS = 3;

    private final NoteAnalysisJobRepository jobRepository = mock(NoteAnalysisJobRepository.class);
    private final NoteAnalysisJobClaimer claimer = mock(NoteAnalysisJobClaimer.class);
    private final NoteAnalysisJobRecorder recorder = mock(NoteAnalysisJobRecorder.class);
    private final NoteAnalyzer noteAnalyzer = mock(NoteAnalyzer.class);
    private Bulkhead retryBulkhead;
    private Bulkhead requestBulkhead;
    private NoteAnalysisJobRunner runner;

    @BeforeEach
    void setUp() {
        BulkheadRegistry registry = BulkheadRegistry.ofDefaults();
        retryBulkhead = registry.bulkhead(NoteAnalysisJobRunner.RETRY_BULKHEAD, limit(RETRY_PERMITS));
        requestBulkhead = registry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD, limit(REQUEST_PERMITS));
        runner = new NoteAnalysisJobRunner(jobRepository, claimer, recorder, noteAnalyzer, registry,
                Schedulers.immediate(), Duration.ofSeconds(3), Duration.ofMillis(200));
        given(jobRepository.findExpiredLeases(eq(NoteAnalysisJobStatus.RUNNING), any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of());
        given(claimer.claim(anyLong(), any(Duration.class), any(LocalDateTime.class)))
                .willAnswer(invocation -> Optional.of(claim(invocation.getArgument(0))));
        given(recorder.complete(any(NoteAnalysisClaim.class), anyList(), any(LocalDateTime.class)))
                .willReturn(NoteAnalysisJobOutcome.COMPLETED);
        given(recorder.fail(any(NoteAnalysisClaim.class), any(NoteAnalysisReason.class), any(LocalDateTime.class)))
                .willReturn(NoteAnalysisJobOutcome.RETRY_SCHEDULED);
    }

    @Test
    void 대기_작업이_재처리_한도보다_많아도_한도만큼만_AI를_부르고_끝나면_허가를_돌려준다() {
        dueJobs(1L, 2L, 3L, 4L, 5L);
        Sinks.One<List<KeywordMatch>> ai = Sinks.one();
        given(noteAnalyzer.classifyAsync(anyString(), anyString())).willReturn(ai.asMono());

        assertThat(runner.dispatch(LocalDateTime.now())).isEqualTo(RETRY_PERMITS);
        assertThat(runner.dispatch(LocalDateTime.now())).isZero();

        verify(noteAnalyzer, times(RETRY_PERMITS)).classifyAsync(anyString(), anyString());
        assertThat(available(retryBulkhead)).isZero();
        ai.tryEmitValue(List.of(new KeywordMatch(1L, "ai")));
        assertThat(available(retryBulkhead)).isEqualTo(RETRY_PERMITS);
    }

    @Test
    void 즉시_요청_허가가_모두_쓰여도_재처리는_자기_허가로_작업을_시작한다() {
        dueJobs(1L);
        given(noteAnalyzer.classifyAsync(anyString(), anyString())).willReturn(Mono.just(List.of()));
        for (int i = 0; i < REQUEST_PERMITS; i++) {
            assertThat(requestBulkhead.tryAcquirePermission()).isTrue();
        }

        assertThat(runner.dispatch(LocalDateTime.now())).isEqualTo(1);

        assertThat(available(requestBulkhead)).isZero();
        assertThat(available(retryBulkhead)).isEqualTo(RETRY_PERMITS);
    }

    @Test
    void AI가_실패하면_실패를_기록하고_허가를_돌려준다() {
        dueJobs(1L);
        given(noteAnalyzer.classifyAsync(anyString(), anyString()))
                .willReturn(Mono.error(new KeywordClassificationException("모두 실패", new RuntimeException("503"))));

        runner.dispatch(LocalDateTime.now());

        verify(recorder).fail(any(NoteAnalysisClaim.class), eq(NoteAnalysisReason.AI_FAILED), any(LocalDateTime.class));
        assertThat(available(retryBulkhead)).isEqualTo(RETRY_PERMITS);
    }

    @Test
    void 결과_저장과_실패_기록이_모두_예외로_끝나도_허가를_돌려준다() {
        dueJobs(1L);
        given(noteAnalyzer.classifyAsync(anyString(), anyString())).willReturn(Mono.just(List.of()));
        given(recorder.complete(any(NoteAnalysisClaim.class), anyList(), any(LocalDateTime.class)))
                .willThrow(new IllegalStateException("DB 연결 끊김"));
        given(recorder.fail(any(NoteAnalysisClaim.class), any(NoteAnalysisReason.class), any(LocalDateTime.class)))
                .willThrow(new IllegalStateException("DB 연결 끊김"));

        runner.dispatch(LocalDateTime.now());

        assertThat(available(retryBulkhead)).isEqualTo(RETRY_PERMITS);
    }

    @Test
    void 작업_상한_시간이_지나면_AI_호출을_취소하고_허가를_돌려준다() {
        dueJobs(1L);
        AtomicBoolean cancelled = new AtomicBoolean();
        given(noteAnalyzer.classifyAsync(anyString(), anyString()))
                .willReturn(Mono.<List<KeywordMatch>>never().doOnCancel(() -> cancelled.set(true)));

        runner.dispatch(LocalDateTime.now());
        assertThat(available(retryBulkhead)).isEqualTo(RETRY_PERMITS - 1);

        await(() -> available(retryBulkhead) == RETRY_PERMITS);
        assertThat(cancelled).isTrue();
        verify(recorder).fail(any(NoteAnalysisClaim.class), eq(NoteAnalysisReason.JOB_TIMEOUT), any(LocalDateTime.class));
    }

    @Test
    void 선점하지_못하거나_선점이_예외로_끝나면_AI를_부르지_않고_허가를_돌려준다() {
        dueJobs(1L, 2L);
        given(claimer.claim(eq(1L), any(Duration.class), any(LocalDateTime.class))).willReturn(Optional.empty());
        given(claimer.claim(eq(2L), any(Duration.class), any(LocalDateTime.class)))
                .willThrow(new IllegalStateException("잠금 대기 초과"));

        assertThat(runner.dispatch(LocalDateTime.now())).isZero();

        verify(noteAnalyzer, never()).classifyAsync(anyString(), anyString());
        assertThat(available(retryBulkhead)).isEqualTo(RETRY_PERMITS);
    }

    @Test
    void 재처리_허가가_모두_쓰이고_있으면_작업을_찾지도_않는다() {
        for (int i = 0; i < RETRY_PERMITS; i++) {
            assertThat(retryBulkhead.tryAcquirePermission()).isTrue();
        }

        assertThat(runner.dispatch(LocalDateTime.now())).isZero();

        verify(jobRepository, never()).findDueAttempts(any(), any(), any());
        verify(claimer, never()).claim(anyLong(), any(Duration.class), any(LocalDateTime.class));
    }

    private void dueJobs(Long... noteIds) {
        given(jobRepository.findDueAttempts(eq(NoteAnalysisJobStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(noteIds));
    }

    private static NoteAnalysisClaim claim(Long noteId) {
        return new NoteAnalysisClaim(noteId, 0L, "token-" + noteId, "제목", "본문");
    }

    private static BulkheadConfig limit(int permits) {
        return BulkheadConfig.custom().maxConcurrentCalls(permits).maxWaitDuration(Duration.ZERO).build();
    }

    private static int available(Bulkhead bulkhead) {
        return bulkhead.getMetrics().getAvailableConcurrentCalls();
    }

    private static void await(Supplier<Boolean> condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("5초 안에 조건을 만족하지 않았습니다");
    }
}
