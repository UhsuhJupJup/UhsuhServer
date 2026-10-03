package uhsuhjupjup.backend.oss.github.application.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubIssueListResultTest {

    private static final String ETAG = "W/\"a1b2c3\"";
    private static final GitHubIssue ISSUE = new GitHubIssue(5612345678L, 1347, "Found a bug", "body", false, 0,
            "octocat", "User", LocalDateTime.of(2026, 9, 28, 17, 11, 25), LocalDateTime.of(2026, 9, 29, 0, 30));

    @Test
    void unchanged_keepsSentEtagWithoutIssuesAsComplete() {
        GitHubIssueListResult result = GitHubIssueListResult.unchanged(ETAG);

        assertThat(result.notModified()).isTrue();
        assertThat(result.etag()).isEqualTo(ETAG);
        assertThat(result.issues()).isEmpty();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void changed_keepsEtagAndIssuesAsComplete() {
        GitHubIssueListResult result = GitHubIssueListResult.changed(ETAG, List.of(ISSUE));

        assertThat(result.notModified()).isFalse();
        assertThat(result.etag()).isEqualTo(ETAG);
        assertThat(result.issues()).containsExactly(ISSUE);
        assertThat(result.complete()).isTrue();
    }

    @Test
    void changed_withoutEtagFromGitHub_keepsEtagNull() {
        assertThat(GitHubIssueListResult.changed(null, List.of(ISSUE)).etag()).isNull();
    }

    @Test
    void partial_hasNoEtagAndIsIncomplete() {
        GitHubIssueListResult result = GitHubIssueListResult.partial(List.of(ISSUE));

        assertThat(result.notModified()).isFalse();
        assertThat(result.etag()).isNull();
        assertThat(result.issues()).containsExactly(ISSUE);
        assertThat(result.complete()).isFalse();
    }

    @Test
    void issues_areCopiedAndCannotBeChangedAfterward() {
        List<GitHubIssue> source = new ArrayList<>(List.of(ISSUE));
        GitHubIssueListResult result = GitHubIssueListResult.changed(ETAG, source);

        source.clear();

        assertThat(result.issues()).containsExactly(ISSUE);
        assertThatThrownBy(() -> result.issues().add(ISSUE)).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void unchanged_withoutEtag_isRejected(String etag) {
        assertThatThrownBy(() -> GitHubIssueListResult.unchanged(etag))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notModifiedWithIssues_isRejected() {
        assertThatThrownBy(() -> new GitHubIssueListResult(true, ETAG, List.of(ISSUE), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notModifiedButIncomplete_isRejected() {
        assertThatThrownBy(() -> new GitHubIssueListResult(true, ETAG, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incompleteWithEtag_isRejected() {
        assertThatThrownBy(() -> new GitHubIssueListResult(false, ETAG, List.of(ISSUE), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
