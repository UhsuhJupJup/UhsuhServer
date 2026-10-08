package uhsuhjupjup.backend.oss.github.application.dto;

import java.util.List;

public record GitHubIssueDetail(
        long githubId,
        String title,
        String body,
        List<String> labels,
        boolean open,
        boolean pullRequest,
        int assigneeCount,
        String authorLogin,
        String authorType) {

    public GitHubIssueDetail {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    @Override
    public String toString() {
        return "GitHubIssueDetail[githubId=" + githubId
                + ", title=" + lengthOf(title)
                + ", body=" + lengthOf(body)
                + ", labels=" + labels.size()
                + ", open=" + open
                + ", pullRequest=" + pullRequest
                + ", assigneeCount=" + assigneeCount
                + ", authorType=" + authorType
                + "]";
    }

    private static String lengthOf(String text) {
        return text == null ? "null" : "<" + text.codePointCount(0, text.length()) + "자>";
    }
}
