package uhsuhjupjup.backend.learningnote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.common.domain.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "note_analysis_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteAnalysisJob extends BaseEntity {

    @Id
    @Column(name = "note_id")
    private Long noteId;

    @Column(name = "note_revision", nullable = false)
    private long noteRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NoteAnalysisJobStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "queued_at", nullable = false)
    private LocalDateTime queuedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "lease_token", length = 36)
    private String leaseToken;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_reason", length = 30)
    private NoteAnalysisReason lastReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    private NoteAnalysisJob(Long noteId) {
        this.noteId = noteId;
    }

    public static NoteAnalysisJob queue(Long noteId, long revision, NoteAnalysisReason reason, int attempts,
                                        LocalDateTime nextAttemptAt, LocalDateTime now) {
        NoteAnalysisJob job = new NoteAnalysisJob(noteId);
        job.restart(revision, reason, attempts, nextAttemptAt, now);
        return job;
    }

    public boolean requeue(long revision, NoteAnalysisReason reason, int attempts, LocalDateTime nextAttemptAt,
                           LocalDateTime now) {
        if (noteRevision == revision && status != NoteAnalysisJobStatus.SUPERSEDED) {
            return false;
        }
        restart(revision, reason, attempts, nextAttemptAt, now);
        return true;
    }

    public boolean retryFailed(long revision, LocalDateTime now) {
        if (status != NoteAnalysisJobStatus.FAILED || noteRevision != revision) {
            return false;
        }
        restart(revision, null, 0, now, now);
        return true;
    }

    public boolean isActiveFor(long revision) {
        return noteRevision == revision
                && (status == NoteAnalysisJobStatus.PENDING || status == NoteAnalysisJobStatus.RUNNING);
    }

    public boolean isFailedFor(long revision) {
        return noteRevision == revision && status == NoteAnalysisJobStatus.FAILED;
    }

    public boolean isUnacknowledgedResultFor(long revision) {
        return noteRevision == revision && status == NoteAnalysisJobStatus.DONE && acknowledgedAt == null;
    }

    public boolean holds(String token) {
        return status == NoteAnalysisJobStatus.RUNNING && token != null && token.equals(leaseToken);
    }

    public boolean complete(String token, LocalDateTime now) {
        if (!holds(token)) {
            return false;
        }
        status = NoteAnalysisJobStatus.DONE;
        completedAt = now;
        acknowledgedAt = null;
        clearLease();
        return true;
    }

    public boolean completeAlreadyAnalyzed(String token, LocalDateTime now) {
        if (!complete(token, now)) {
            return false;
        }
        acknowledgedAt = now;
        return true;
    }

    public boolean failIfExhausted(String token, LocalDateTime now, NoteAnalysisRetryPolicy policy) {
        if (!holds(token) || !policy.exhausted(attempts, queuedAt, now)) {
            return false;
        }
        status = NoteAnalysisJobStatus.FAILED;
        clearLease();
        return true;
    }

    public boolean supersede(String token) {
        if (!holds(token)) {
            return false;
        }
        status = NoteAnalysisJobStatus.SUPERSEDED;
        clearLease();
        return true;
    }

    public boolean retryLater(String token, NoteAnalysisReason reason, LocalDateTime now,
                              NoteAnalysisRetryPolicy policy) {
        if (!holds(token)) {
            return false;
        }
        attempts++;
        lastReason = reason;
        clearLease();
        if (policy.exhausted(attempts, queuedAt, now)) {
            status = NoteAnalysisJobStatus.FAILED;
            return true;
        }
        status = NoteAnalysisJobStatus.PENDING;
        nextAttemptAt = policy.nextAttemptAt(attempts, now);
        return true;
    }

    public boolean deferWhileCircuitOpen(String token, LocalDateTime now, NoteAnalysisRetryPolicy policy) {
        if (!holds(token)) {
            return false;
        }
        lastReason = NoteAnalysisReason.CIRCUIT_OPEN;
        clearLease();
        if (!now.isBefore(queuedAt.plus(policy.horizon()))) {
            status = NoteAnalysisJobStatus.FAILED;
            return true;
        }
        status = NoteAnalysisJobStatus.PENDING;
        nextAttemptAt = policy.circuitOpenUntil(now);
        return true;
    }

    private void restart(long revision, NoteAnalysisReason reason, int attempts, LocalDateTime nextAttemptAt,
                         LocalDateTime now) {
        this.noteRevision = revision;
        this.status = NoteAnalysisJobStatus.PENDING;
        this.attempts = attempts;
        this.queuedAt = now;
        this.nextAttemptAt = nextAttemptAt;
        this.lastReason = reason;
        this.completedAt = null;
        this.acknowledgedAt = null;
        clearLease();
    }

    private void clearLease() {
        this.leaseToken = null;
        this.leaseUntil = null;
    }
}
