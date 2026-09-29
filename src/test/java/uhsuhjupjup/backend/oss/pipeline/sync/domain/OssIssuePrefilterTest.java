package uhsuhjupjup.backend.oss.pipeline.sync.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter.ExclusionReason;

import static org.assertj.core.api.Assertions.assertThat;

class OssIssuePrefilterTest {

    private static final String HUMAN_LOGIN = "octocat";

    @Test
    void reasonToExclude_unassignedIssueByHuman_keepsIt() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, HUMAN_LOGIN, OssIssueAuthorType.OTHER)).isEmpty();
    }

    @Test
    void reasonToExclude_pullRequest_excludesAsPullRequest() {
        assertThat(OssIssuePrefilter.reasonToExclude(true, 0, HUMAN_LOGIN, OssIssueAuthorType.OTHER))
                .contains(ExclusionReason.PULL_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void reasonToExclude_oneOrMoreAssignees_excludesAsAssigned(int assigneeCount) {
        assertThat(OssIssuePrefilter.reasonToExclude(false, assigneeCount, HUMAN_LOGIN, OssIssueAuthorType.OTHER))
                .contains(ExclusionReason.ASSIGNED);
    }

    @Test
    void reasonToExclude_negativeAssigneeCount_keepsItAsUnassigned() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, -1, HUMAN_LOGIN, OssIssueAuthorType.OTHER)).isEmpty();
    }

    @Test
    void reasonToExclude_botTypeWithoutBotSuffix_excludesAsBotAuthor() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, "Copilot", OssIssueAuthorType.BOT))
                .contains(ExclusionReason.BOT_AUTHOR);
    }

    @Test
    void reasonToExclude_botSuffixWithNonBotType_excludesAsBotAuthor() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, "github-actions[bot]", OssIssueAuthorType.OTHER))
                .contains(ExclusionReason.BOT_AUTHOR);
    }

    @Test
    void reasonToExclude_appBotWithBotTypeAndSuffix_excludesAsBotAuthor() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, "dependabot[bot]", OssIssueAuthorType.BOT))
                .contains(ExclusionReason.BOT_AUTHOR);
    }

    @Test
    void reasonToExclude_loginEndingWithBotWithoutBrackets_keepsIt() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, "renovate-bot", OssIssueAuthorType.OTHER)).isEmpty();
    }

    @Test
    void reasonToExclude_emptyLogin_isNotTreatedAsBot() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, "", OssIssueAuthorType.OTHER)).isEmpty();
    }

    @Test
    void reasonToExclude_emptyGithubType_isNotTreatedAsBot() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, HUMAN_LOGIN, OssIssueAuthorType.from(""))).isEmpty();
    }

    @Test
    void reasonToExclude_missingAuthor_isNotTreatedAsBot() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, null, OssIssueAuthorType.from(null))).isEmpty();
    }

    @Test
    void reasonToExclude_missingLoginWithBotType_excludesAsBotAuthor() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, null, OssIssueAuthorType.BOT))
                .contains(ExclusionReason.BOT_AUTHOR);
    }

    @Test
    void reasonToExclude_missingGithubTypeWithBotSuffix_excludesAsBotAuthor() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 0, "github-actions[bot]", OssIssueAuthorType.from(null)))
                .contains(ExclusionReason.BOT_AUTHOR);
    }

    @Test
    void reasonToExclude_assignedPullRequestByBot_reportsPullRequest() {
        assertThat(OssIssuePrefilter.reasonToExclude(true, 1, "dependabot[bot]", OssIssueAuthorType.BOT))
                .contains(ExclusionReason.PULL_REQUEST);
    }

    @Test
    void reasonToExclude_assignedPullRequestByHuman_reportsPullRequest() {
        assertThat(OssIssuePrefilter.reasonToExclude(true, 1, HUMAN_LOGIN, OssIssueAuthorType.OTHER))
                .contains(ExclusionReason.PULL_REQUEST);
    }

    @Test
    void reasonToExclude_assignedIssueByBot_reportsBotAuthor() {
        assertThat(OssIssuePrefilter.reasonToExclude(false, 1, "github-actions[bot]", OssIssueAuthorType.BOT))
                .contains(ExclusionReason.BOT_AUTHOR);
    }
}
