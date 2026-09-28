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
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Entity
@Table(name = "oss_issue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssIssue extends BaseEntity {

    private static final String BODY_HASH_ALGORITHM = "SHA-256";
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

    private OssIssue(OssRepo repo, Long githubIssueId, int number, String title, String body,
                     LocalDateTime githubCreatedAt) {
        this.repo = repo;
        this.githubIssueId = githubIssueId;
        this.number = number;
        this.title = truncateTitle(title);
        this.bodyHash = hashOf(body);
        this.githubCreatedAt = githubCreatedAt;
    }

    public static OssIssue create(OssRepo repo, Long githubIssueId, int number, String title, String body,
                                  LocalDateTime githubCreatedAt) {
        return new OssIssue(repo, githubIssueId, number, title, body, githubCreatedAt);
    }

    private static String truncateTitle(String title) {
        if (title.codePointCount(0, title.length()) <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, MAX_TITLE_LENGTH));
    }

    private static String hashOf(String body) {
        byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(BODY_HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
