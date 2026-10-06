package uhsuhjupjup.backend.oss.github.application.dto;

import java.util.List;

public record GitHubIssueListResult(
        boolean notModified,
        String etag,
        List<GitHubIssue> issues,
        IncompleteReason incompleteReason) {

    public enum IncompleteReason {
        PAGE_LIMIT,
        PAGE_UNAVAILABLE
    }

    private static final String INVALID_NOT_MODIFIED =
            "바뀐 것 없음 결과는 보낸 ETag를 담고 이슈 없이 끝까지 읽은 결과여야 합니다.";
    private static final String INVALID_INCOMPLETE = "끝까지 읽지 못한 결과는 ETag를 담지 않습니다.";
    private static final String MISSING_INCOMPLETE_REASON = "끝까지 읽지 못한 결과는 그 이유를 담아야 합니다.";

    public GitHubIssueListResult {
        issues = List.copyOf(issues);
        if (notModified && (etag == null || etag.isBlank() || !issues.isEmpty() || incompleteReason != null)) {
            throw new IllegalArgumentException(INVALID_NOT_MODIFIED);
        }
        if (incompleteReason != null && etag != null) {
            throw new IllegalArgumentException(INVALID_INCOMPLETE);
        }
    }

    public static GitHubIssueListResult unchanged(String etag) {
        return new GitHubIssueListResult(true, etag, List.of(), null);
    }

    public static GitHubIssueListResult changed(String etag, List<GitHubIssue> issues) {
        return new GitHubIssueListResult(false, etag, issues, null);
    }

    public static GitHubIssueListResult partial(List<GitHubIssue> issues, IncompleteReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException(MISSING_INCOMPLETE_REASON);
        }
        return new GitHubIssueListResult(false, null, issues, reason);
    }

    public boolean complete() {
        return incompleteReason == null;
    }
}
