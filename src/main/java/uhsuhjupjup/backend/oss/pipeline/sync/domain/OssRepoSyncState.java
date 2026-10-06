package uhsuhjupjup.backend.oss.pipeline.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.common.domain.BaseEntity;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "oss_repo_sync_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssRepoSyncState extends BaseEntity {

    private static final Duration FIRST_SYNC_WINDOW = Duration.ofDays(7);
    private static final Duration RESYNC_OVERLAP = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private OssRepo repo;

    @Column(name = "etag", length = 255)
    private String etag;

    @Column(name = "last_issue_updated_at")
    private LocalDateTime lastIssueUpdatedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    private OssRepoSyncState(OssRepo repo) {
        this.repo = repo;
        this.consecutiveFailures = 0;
    }

    public static OssRepoSyncState create(OssRepo repo) {
        return new OssRepoSyncState(repo);
    }

    public LocalDateTime issuesUpdatedSince(LocalDateTime now) {
        if (lastIssueUpdatedAt == null) {
            return now.minus(FIRST_SYNC_WINDOW);
        }
        if (etag == null) {
            return lastIssueUpdatedAt;
        }
        return lastIssueUpdatedAt.minus(RESYNC_OVERLAP);
    }

    public void recordRead(LocalDateTime latestIssueUpdatedAt, String etag, LocalDateTime syncedAt) {
        advanceLastIssueUpdatedAt(latestIssueUpdatedAt);
        this.etag = etag;
        this.lastSyncedAt = syncedAt;
        this.consecutiveFailures = 0;
    }

    public void recordReadCutByFailure(LocalDateTime latestIssueUpdatedAt, LocalDateTime syncedAt) {
        advanceLastIssueUpdatedAt(latestIssueUpdatedAt);
        this.etag = null;
        this.lastSyncedAt = syncedAt;
        this.consecutiveFailures++;
    }

    public void recordNotModified(LocalDateTime syncedAt) {
        this.lastSyncedAt = syncedAt;
        this.consecutiveFailures = 0;
    }

    public void recordFailure() {
        this.consecutiveFailures++;
    }

    private void advanceLastIssueUpdatedAt(LocalDateTime latestIssueUpdatedAt) {
        if (latestIssueUpdatedAt == null) {
            return;
        }
        if (lastIssueUpdatedAt == null || latestIssueUpdatedAt.isAfter(lastIssueUpdatedAt)) {
            lastIssueUpdatedAt = latestIssueUpdatedAt;
        }
    }
}
