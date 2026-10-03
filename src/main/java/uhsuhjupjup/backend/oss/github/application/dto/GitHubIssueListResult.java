package uhsuhjupjup.backend.oss.github.application.dto;

import java.util.List;

public record GitHubIssueListResult(
        boolean notModified,
        String etag,
        List<GitHubIssue> issues,
        boolean complete) {

    private static final String INVALID_NOT_MODIFIED =
            "바뀐 것 없음 결과는 보낸 ETag를 담고 이슈 없이 끝까지 읽은 결과여야 합니다.";
    private static final String INVALID_INCOMPLETE = "끝까지 읽지 못한 결과는 ETag를 담지 않습니다.";

    public GitHubIssueListResult {
        issues = List.copyOf(issues);
        if (notModified && (etag == null || etag.isBlank() || !issues.isEmpty() || !complete)) {
            throw new IllegalArgumentException(INVALID_NOT_MODIFIED);
        }
        if (!complete && etag != null) {
            throw new IllegalArgumentException(INVALID_INCOMPLETE);
        }
    }

    public static GitHubIssueListResult unchanged(String etag) {
        return new GitHubIssueListResult(true, etag, List.of(), true);
    }

    public static GitHubIssueListResult changed(String etag, List<GitHubIssue> issues) {
        return new GitHubIssueListResult(false, etag, issues, true);
    }

    public static GitHubIssueListResult partial(List<GitHubIssue> issues) {
        return new GitHubIssueListResult(false, null, issues, false);
    }
}
