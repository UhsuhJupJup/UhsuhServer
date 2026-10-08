package uhsuhjupjup.backend.oss.issue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.common.domain.BaseEntity;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.LocalDateTime;

@Entity
@Table(name = "oss_issue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssIssue extends BaseEntity {

    private static final int MAX_TITLE_LENGTH = 256;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private OssRepo repo;

    @Column(name = "github_issue_id", nullable = false)
    private Long githubIssueId;

    @Column(name = "number", nullable = false)
    private int number;

    @Column(name = "title", nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "body_hash", nullable = false, length = 64)
    private String bodyHash;

    @Column(name = "github_created_at", nullable = false)
    private LocalDateTime githubCreatedAt;

    @Column(name = "grading_failures", nullable = false, insertable = false, updatable = false)
    private int gradingFailures;

    @Column(name = "grading_failure_source_hash", length = 64, insertable = false, updatable = false)
    private String gradingFailureSourceHash;

    private OssIssue(OssRepo repo, Long githubIssueId, int number, String title, String body,
                     LocalDateTime githubCreatedAt) {
        this.repo = repo;
        this.githubIssueId = githubIssueId;
        this.number = number;
        this.title = truncateTitle(title);
        this.bodyHash = OssIssueBodyHash.of(body);
        this.githubCreatedAt = githubCreatedAt;
    }

    public static OssIssue create(OssRepo repo, Long githubIssueId, int number, String title, String body,
                                  LocalDateTime githubCreatedAt) {
        return new OssIssue(repo, githubIssueId, number, title, body, githubCreatedAt);
    }

    public void refresh(OssRepo repo, int number, String title, String body) {
        this.repo = repo;
        this.number = number;
        this.title = truncateTitle(title);
        this.bodyHash = OssIssueBodyHash.of(body);
    }

    public boolean gradingFailedAtLeast(int times, String sourceHash) {
        return gradingFailures >= times && sourceHash.equals(gradingFailureSourceHash);
    }

    public boolean hasGradingFailures() {
        return gradingFailures > 0 || gradingFailureSourceHash != null;
    }

    private static String truncateTitle(String title) {
        if (title.codePointCount(0, title.length()) <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, MAX_TITLE_LENGTH));
    }
}
