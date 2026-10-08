package uhsuhjupjup.backend.oss.issue.ui.dto;

final class GitHubIssueUrl {

    private static final String PREFIX = "https://github.com/";
    private static final String ISSUES_PATH = "/issues/";

    private GitHubIssueUrl() {
    }

    static String of(String repoFullName, int number) {
        return PREFIX + repoFullName + ISSUES_PATH + number;
    }
}
