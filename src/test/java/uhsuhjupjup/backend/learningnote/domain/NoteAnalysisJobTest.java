package uhsuhjupjup.backend.learningnote.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NoteAnalysisJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 10, 0);
    private static final NoteAnalysisRetryPolicy POLICY = new NoteAnalysisRetryPolicy(
            Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(6), 3, Duration.ofSeconds(30));

    @Test
    void 처음_보존하면_대기_상태다() {
        NoteAnalysisJob job = NoteAnalysisJob.queue(1L, 0L, NoteAnalysisReason.CAPACITY_EXCEEDED, 0, NOW, NOW);

        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        assertThat(job.isActiveFor(0L)).isTrue();
        assertThat(job.isActiveFor(1L)).isFalse();
    }

    @Test
    void 같은_버전의_작업이_있으면_다시_보존해도_바꾸지_않는다() {
        NoteAnalysisJob job = NoteAnalysisJob.queue(1L, 0L, NoteAnalysisReason.AI_FAILED, 1, NOW.plusSeconds(30), NOW);

        boolean changed = job.requeue(0L, NoteAnalysisReason.CAPACITY_EXCEEDED, 0, NOW, NOW.plusSeconds(5));

        assertThat(changed).isFalse();
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getLastReason()).isEqualTo(NoteAnalysisReason.AI_FAILED);
    }

    @Test
    void 노트_버전이_바뀌면_새_버전으로_처음부터_보존한다() {
        NoteAnalysisJob job = NoteAnalysisJob.queue(1L, 0L, NoteAnalysisReason.AI_FAILED, 1, NOW, NOW);
        running(job, "a");
        job.supersede("a");

        boolean changed = job.requeue(1L, NoteAnalysisReason.CAPACITY_EXCEEDED, 0, NOW, NOW.plusMinutes(1));

        assertThat(changed).isTrue();
        assertThat(job.getNoteRevision()).isEqualTo(1L);
        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getQueuedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    void 임대_토큰이_다르면_결과를_기록하지_않는다() {
        NoteAnalysisJob job = NoteAnalysisJob.queue(1L, 0L, NoteAnalysisReason.CAPACITY_EXCEEDED, 0, NOW, NOW);
        running(job, "current");

        assertThat(job.complete("expired", NOW)).isFalse();
        assertThat(job.retryLater("expired", NoteAnalysisReason.AI_FAILED, NOW, POLICY)).isFalse();
        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.RUNNING);
        assertThat(job.complete("current", NOW)).isTrue();
        assertThat(job.isUnacknowledgedResultFor(0L)).isTrue();
    }

    @Test
    void 실패하면_시도_횟수를_늘리고_간격을_두_배로_늘린다() {
        NoteAnalysisJob job = NoteAnalysisJob.queue(1L, 0L, NoteAnalysisReason.CAPACITY_EXCEEDED, 0, NOW, NOW);
        running(job, "a");
        job.retryLater("a", NoteAnalysisReason.AI_FAILED, NOW, POLICY);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));

        running(job, "b");
        job.retryLater("b", NoteAnalysisReason.AI_FAILED, NOW, POLICY);

        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void 최대_시도_횟수에_닿으면_실패로_끝낸다() {
        NoteAnalysisJob job = NoteAnalysisJob.queue(1L, 0L, NoteAnalysisReason.AI_FAILED, 2, NOW, NOW);
        running(job, "a");

        job.retryLater("a", NoteAnalysisReason.AI_FAILED, NOW, POLICY);

        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.FAILED);
        assertThat(job.isFailedFor(0L)).isTrue();
    }

    @Test
    void 서킷이_열린_동안은_시도_횟수를_늘리지_않고_미룬다() {
        NoteAnalysisJob job = NoteAnalysisJob.queue(1L, 0L, NoteAnalysisReason.CAPACITY_EXCEEDED, 0, NOW, NOW);
        running(job, "a");

        job.deferWhileCircuitOpen("a", NOW, POLICY);

        assertThat(job.getAttempts()).isZero();
        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void 서킷이_열려_있어도_시간_지평을_넘기면_실패로_끝낸다() {
        NoteAnalysisJob job = NoteAnalysisJob.queue(1L, 0L, NoteAnalysisReason.CAPACITY_EXCEEDED, 0, NOW, NOW);
        running(job, "a");

        job.deferWhileCircuitOpen("a", NOW.plusHours(6), POLICY);

        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.FAILED);
    }

    @Test
    void 실패한_작업만_사용자가_다시_맡길_수_있다() {
        NoteAnalysisJob job = NoteAnalysisJob.queue(1L, 0L, NoteAnalysisReason.AI_FAILED, 2, NOW, NOW);
        assertThat(job.retryFailed(0L, NOW)).isFalse();
        running(job, "a");
        job.retryLater("a", NoteAnalysisReason.AI_FAILED, NOW, POLICY);

        assertThat(job.retryFailed(1L, NOW)).isFalse();
        assertThat(job.retryFailed(0L, NOW.plusHours(1))).isTrue();
        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getQueuedAt()).isEqualTo(NOW.plusHours(1));
    }

    private static void running(NoteAnalysisJob job, String token) {
        ReflectionTestUtils.setField(job, "status", NoteAnalysisJobStatus.RUNNING);
        ReflectionTestUtils.setField(job, "leaseToken", token);
        ReflectionTestUtils.setField(job, "leaseUntil", NOW.plusMinutes(3));
    }
}
