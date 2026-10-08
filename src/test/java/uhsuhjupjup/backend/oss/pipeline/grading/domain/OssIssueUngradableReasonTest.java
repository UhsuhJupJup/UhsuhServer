package uhsuhjupjup.backend.oss.pipeline.grading.domain;

import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssueAuthorType;

import static org.assertj.core.api.Assertions.assertThat;

class OssIssueUngradableReasonTest {

    private static final String HUMAN_LOGIN = "octocat";

    @Test
    void of_openUnassignedIssueByHumanThatIsOurIssue_canBeGraded() {
        assertThat(OssIssueUngradableReason.of(false, true, false, 0, HUMAN_LOGIN, OssIssueAuthorType.OTHER))
                .isEmpty();
    }

    @Test
    void of_anotherIssueAtOurAddress_isIdMismatchWhateverElseItIs() {
        assertThat(OssIssueUngradableReason.of(true, false, true, 2, "renovate[bot]", OssIssueAuthorType.BOT))
                .contains(OssIssueUngradableReason.ID_MISMATCH);
    }

    @Test
    void of_closedIssue_isClosedEvenIfOtherRulesAlsoApply() {
        assertThat(OssIssueUngradableReason.of(false, false, false, 1, "renovate[bot]", OssIssueAuthorType.BOT))
                .contains(OssIssueUngradableReason.CLOSED);
    }

    @Test
    void of_pullRequest_isPullRequest() {
        assertThat(OssIssueUngradableReason.of(false, true, true, 0, HUMAN_LOGIN, OssIssueAuthorType.OTHER))
                .contains(OssIssueUngradableReason.PULL_REQUEST);
    }

    @Test
    void of_botTypeOrBotLogin_isBotAuthor() {
        assertThat(OssIssueUngradableReason.of(false, true, false, 0, "Copilot", OssIssueAuthorType.BOT))
                .contains(OssIssueUngradableReason.BOT_AUTHOR);
        assertThat(OssIssueUngradableReason.of(false, true, false, 0, "github-actions[bot]", OssIssueAuthorType.OTHER))
                .contains(OssIssueUngradableReason.BOT_AUTHOR);
    }

    @Test
    void of_issueWithAssignee_isAssigned() {
        assertThat(OssIssueUngradableReason.of(false, true, false, 1, HUMAN_LOGIN, OssIssueAuthorType.OTHER))
                .contains(OssIssueUngradableReason.ASSIGNED);
    }

    @Test
    void of_openIssueFailingSeveralPrefilterRules_takesThePrefilterOrder() {
        assertThat(OssIssueUngradableReason.of(false, true, true, 1, "dependabot[bot]", OssIssueAuthorType.BOT))
                .contains(OssIssueUngradableReason.PULL_REQUEST);
        assertThat(OssIssueUngradableReason.of(false, true, false, 1, "dependabot[bot]", OssIssueAuthorType.BOT))
                .contains(OssIssueUngradableReason.BOT_AUTHOR);
    }

    @Test
    void of_missingAuthor_isNotTreatedAsBot() {
        assertThat(OssIssueUngradableReason.of(false, true, false, 0, null, OssIssueAuthorType.from(null))).isEmpty();
    }
}
