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
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Entity
@Table(name = "oss_issue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssIssue extends BaseEntity {

    private static final String BODY_HASH_ALGORITHM = "SHA-256";
    private static final int MAX_TITLE_LENGTH = 256;
    private static final String CRLF = "\r\n";
    private static final char CR = '\r';
    private static final char LF = '\n';
    private static final String LINE_BREAK = String.valueOf(LF);

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

    public void refresh(OssRepo repo, int number, String title, String body) {
        this.repo = repo;
        this.number = number;
        this.title = truncateTitle(title);
        this.bodyHash = hashOf(body);
    }

    private static String truncateTitle(String title) {
        if (title.codePointCount(0, title.length()) <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, MAX_TITLE_LENGTH));
    }

    private static String hashOf(String body) {
        byte[] bytes = normalizeBody(body).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static String normalizeBody(String body) {
        if (body == null) {
            return "";
        }
        List<String> lines = Arrays.stream(body.replace(CRLF, LINE_BREAK).replace(CR, LF).split(LINE_BREAK, -1))
                .map(OssIssue::stripTrailingSpacesAndTabs)
                .toList();
        int first = 0;
        int end = lines.size();
        while (first < end && lines.get(first).isEmpty()) {
            first++;
        }
        while (end > first && lines.get(end - 1).isEmpty()) {
            end--;
        }
        return String.join(LINE_BREAK, lines.subList(first, end));
    }

    private static String stripTrailingSpacesAndTabs(String line) {
        int end = line.length();
        while (end > 0 && isSpaceOrTab(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    private static boolean isSpaceOrTab(char character) {
        return character == ' ' || character == '\t';
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(BODY_HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
