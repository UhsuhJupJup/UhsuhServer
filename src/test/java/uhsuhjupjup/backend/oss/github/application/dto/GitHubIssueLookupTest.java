package uhsuhjupjup.backend.oss.github.application.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueLookup.Status;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubIssueLookupTest {

    private static final GitHubIssueDetail ISSUE =
            new GitHubIssueDetail(5612345678L, "Found a bug", "body", List.of(), true, false, 0, "octocat", "User");

    @Test
    void found_keepsTheIssue() {
        GitHubIssueLookup lookup = GitHubIssueLookup.found(ISSUE);

        assertThat(lookup.status()).isEqualTo(Status.FOUND);
        assertThat(lookup.issue()).isSameAs(ISSUE);
    }

    @Test
    void goneAndMoved_haveNoIssue() {
        assertThat(GitHubIssueLookup.gone()).isEqualTo(new GitHubIssueLookup(Status.GONE, null));
        assertThat(GitHubIssueLookup.moved()).isEqualTo(new GitHubIssueLookup(Status.MOVED, null));
    }

    @Test
    void found_withoutIssue_isRefused() {
        assertThatThrownBy(() -> GitHubIssueLookup.found(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"GONE", "MOVED"})
    void goneOrMoved_withIssue_isRefused(Status status) {
        assertThatThrownBy(() -> new GitHubIssueLookup(status, ISSUE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingStatus_isRefused() {
        assertThatThrownBy(() -> new GitHubIssueLookup(null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
