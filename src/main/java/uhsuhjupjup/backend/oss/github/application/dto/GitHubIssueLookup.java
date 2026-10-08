package uhsuhjupjup.backend.oss.github.application.dto;

public record GitHubIssueLookup(Status status, GitHubIssueDetail issue) {

    public enum Status {
        FOUND,
        GONE,
        MOVED
    }

    private static final String ISSUE_ONLY_WHEN_FOUND = "찾은 결과만 이슈를 담고, 찾은 결과는 이슈를 꼭 담습니다.";

    public GitHubIssueLookup {
        if (status == null || (status == Status.FOUND) != (issue != null)) {
            throw new IllegalArgumentException(ISSUE_ONLY_WHEN_FOUND);
        }
    }

    public static GitHubIssueLookup found(GitHubIssueDetail issue) {
        return new GitHubIssueLookup(Status.FOUND, issue);
    }

    public static GitHubIssueLookup gone() {
        return new GitHubIssueLookup(Status.GONE, null);
    }

    public static GitHubIssueLookup moved() {
        return new GitHubIssueLookup(Status.MOVED, null);
    }
}
