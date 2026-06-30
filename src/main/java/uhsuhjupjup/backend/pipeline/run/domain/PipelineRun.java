package uhsuhjupjup.backend.pipeline.run.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RunStatus status;

    @Column(name = "collected_total", nullable = false)
    private int collectedTotal;

    @Column(name = "collected_new", nullable = false)
    private int collectedNew;

    @Column(name = "collect_failed", nullable = false)
    private int collectFailed;

    @Column(name = "matched_articles", nullable = false)
    private int matchedArticles;

    @Column(name = "tags_created", nullable = false)
    private int tagsCreated;

    @Column(name = "members_notified", nullable = false)
    private int membersNotified;

    @Column(name = "notifications_recorded", nullable = false)
    private int notificationsRecorded;

    @Column(name = "notify_failed", nullable = false)
    private int notifyFailed;

    @Builder
    private PipelineRun(LocalDateTime startedAt, LocalDateTime finishedAt, RunStatus status,
                        int collectedTotal, int collectedNew, int collectFailed,
                        int matchedArticles, int tagsCreated,
                        int membersNotified, int notificationsRecorded, int notifyFailed) {
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.collectedTotal = collectedTotal;
        this.collectedNew = collectedNew;
        this.collectFailed = collectFailed;
        this.matchedArticles = matchedArticles;
        this.tagsCreated = tagsCreated;
        this.membersNotified = membersNotified;
        this.notificationsRecorded = notificationsRecorded;
        this.notifyFailed = notifyFailed;
    }
}
