package uhsuhjupjup.backend.oss.github.application.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubIssueDetailTest {

    @Test
    void labels_areCopiedAndCannotBeChanged() {
        List<String> labels = new ArrayList<>(List.of("bug"));
        GitHubIssueDetail issue = issueWithLabels(labels);

        labels.add("added later");

        assertThat(issue.labels()).containsExactly("bug");
        assertThatThrownBy(() -> issue.labels().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingLabels_becomeNoLabels() {
        assertThat(issueWithLabels(null).labels()).isEmpty();
    }

    @Test
    void toString_showsTitleAndBodyLengthsInsteadOfTheirTexts() {
        GitHubIssueDetail issue = new GitHubIssueDetail(5612345678L, "TITLEMARKER 🐛", "BODYMARKER",
                List.of("bug", "good first issue"), true, false, 0, "octocat", "User");

        assertThat(issue.toString()).isEqualTo("GitHubIssueDetail[githubId=5612345678, title=<13자>, body=<10자>, "
                + "labels=2, open=true, pullRequest=false, assigneeCount=0, authorType=User]");
    }

    @Test
    void toString_ofIssueWithoutBody_saysNull() {
        GitHubIssueDetail issue = new GitHubIssueDetail(1L, "t", null, List.of(), false, false, 0, null, null);

        assertThat(issue.toString()).contains("body=null");
    }

    private static GitHubIssueDetail issueWithLabels(List<String> labels) {
        return new GitHubIssueDetail(5612345678L, "Found a bug", "body", labels, true, false, 0, "octocat", "User");
    }
}
