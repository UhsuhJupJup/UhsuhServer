package uhsuhjupjup.backend.oss.issue.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OssIssueTest {

    private static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 28, 17, 11, 25);

    private final OssRepo repo = OssRepo.create(6296790L, "spring-projects/spring-boot", null, "Java", 80_000);

    @Test
    void create_keepsIssueFieldsAndRepo() {
        OssIssue issue = OssIssue.create(repo, 5_611_425_470L, 51_878, "Retry interval is ignored", "abc", OPENED_AT);

        assertThat(issue.getRepo()).isSameAs(repo);
        assertThat(issue.getGithubIssueId()).isEqualTo(5_611_425_470L);
        assertThat(issue.getNumber()).isEqualTo(51_878);
        assertThat(issue.getTitle()).isEqualTo("Retry interval is ignored");
        assertThat(issue.getGithubCreatedAt()).isEqualTo(OPENED_AT);
    }

    @Test
    void create_keepsSha256HexOfBodyInsteadOfBody() {
        assertThat(issueWithBody("abc").getBodyHash())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void create_hashesBodyAsUtf8BytesAfterTurningCrLfIntoLf() {
        assertThat(issueWithBody("재현 방법\r\n1. 설정 파일을 읽는다").getBodyHash())
                .isEqualTo("446646d1c572903a4d2425019239923efecd39ce64e1403262d17758a4fae961");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Steps\r\n1. run it\r\n2. see it fail", "Steps\r1. run it\r2. see it fail",
            "Steps\r\n1. run it\r2. see it fail"})
    void create_crLfAndLoneCrLineBreaks_hashLikeLf(String body) {
        assertThat(issueWithBody(body).getBodyHash())
                .isEqualTo(issueWithBody("Steps\n1. run it\n2. see it fail").getBodyHash());
    }

    @Test
    void create_spacesAndTabsAtLineEnds_areIgnored() {
        assertThat(issueWithBody("Steps \t\n1. run it  \n2. see it fail\t").getBodyHash())
                .isEqualTo(issueWithBody("Steps\n1. run it\n2. see it fail").getBodyHash());
    }

    @Test
    void create_blankLinesBeforeAndAfterBody_areIgnored() {
        assertThat(issueWithBody("\n  \n\t\r\nSteps\n1. run it\n\n \t\n").getBodyHash())
                .isEqualTo(issueWithBody("Steps\n1. run it").getBodyHash());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\n", "\r\n\t \r", "  \n\t\n"})
    void create_bodyOfOnlySpacesTabsAndLineBreaks_hashesAsEmptyBody(String body) {
        assertThat(issueWithBody(body).getBodyHash()).isEqualTo(EMPTY_BODY_SHA256);
    }

    @Test
    void create_indentationAtLineStart_isKept() {
        assertThat(issueWithBody("```\n    int retries = 3;\n```").getBodyHash())
                .isNotEqualTo(issueWithBody("```\nint retries = 3;\n```").getBodyHash());
    }

    @Test
    void create_blankLineInsideBody_isKept() {
        assertThat(issueWithBody("Steps\n\n1. run it").getBodyHash())
                .isNotEqualTo(issueWithBody("Steps\n1. run it").getBodyHash());
    }

    @Test
    void create_whitespaceOtherThanSpaceOrTabAtLineEnd_isKept() {
        assertThat(issueWithBody("Steps\u00A0").getBodyHash())
                .isNotEqualTo(issueWithBody("Steps").getBodyHash());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void create_missingOrEmptyBody_hashesAsEmptyBody(String body) {
        assertThat(issueWithBody(body).getBodyHash()).isEqualTo(EMPTY_BODY_SHA256);
    }

    @Test
    void create_bodyChangedByOneCharacter_changesHash() {
        assertThat(issueWithBody("Steps to reproduce.").getBodyHash())
                .isNotEqualTo(issueWithBody("Steps to reproduce!").getBodyHash());
    }

    @Test
    void create_titleOf256CodePoints_isKeptWholeEvenWhenLongerInUtf16() {
        String title = "🐛".repeat(128) + "가".repeat(128);

        assertThat(title).hasSize(384);
        assertThat(issueWithTitle(title).getTitle()).isEqualTo(title);
    }

    @Test
    void create_titleOver256CodePoints_isCutTo256WithoutEllipsis() {
        assertThat(issueWithTitle("a".repeat(257)).getTitle()).isEqualTo("a".repeat(256));
    }

    @Test
    void create_titleOfFourByteCharacters_isCutBy256CodePointsNotUtf16Units() {
        assertThat(issueWithTitle("🐛".repeat(300)).getTitle()).isEqualTo("🐛".repeat(256));
    }

    @Test
    void create_emojiStraddlingTheCut_staysWhole() {
        String title = issueWithTitle("a".repeat(255) + "🐛🐛").getTitle();

        assertThat(title).isEqualTo("a".repeat(255) + "🐛");
        assertThat(title.codePointCount(0, title.length())).isEqualTo(256);
    }

    @Test
    void refresh_cutsTitleAndHashesBodyByTheSameRulesAsCreate() {
        OssIssue issue = issueWithBody("abc");

        issue.refresh(repo, 51_878, "a".repeat(255) + "🐛🐛", "재현 방법\r\n1. 설정 파일을 읽는다  \r\n");

        assertThat(issue.getTitle()).isEqualTo("a".repeat(255) + "🐛");
        assertThat(issue.getBodyHash()).isEqualTo("446646d1c572903a4d2425019239923efecd39ce64e1403262d17758a4fae961");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void refresh_missingOrEmptyBody_hashesAsEmptyBody(String body) {
        OssIssue issue = issueWithBody("abc");

        issue.refresh(repo, 51_878, "Retry interval is ignored", body);

        assertThat(issue.getBodyHash()).isEqualTo(EMPTY_BODY_SHA256);
    }

    @Test
    void refresh_movesIssueToGivenRepoAndNumberKeepingGithubIdAndOpenedAt() {
        OssRepo transferredTo = OssRepo.create(1296269L, "octocat/Hello-World", null, "Java", 80);
        OssIssue issue = issueWithBody("abc");

        issue.refresh(transferredTo, 7, "Retry interval is ignored", "abc");

        assertThat(issue.getRepo()).isSameAs(transferredTo);
        assertThat(issue.getNumber()).isEqualTo(7);
        assertThat(issue.getGithubIssueId()).isEqualTo(5_611_425_470L);
        assertThat(issue.getGithubCreatedAt()).isEqualTo(OPENED_AT);
    }

    @Test
    void create_startsWithoutGradingFailures() {
        OssIssue issue = issueWithBody("abc");

        assertThat(issue.getGradingFailures()).isZero();
        assertThat(issue.getGradingFailureSourceHash()).isNull();
        assertThat(issue.gradingFailedAtLeast(1, issue.getBodyHash())).isFalse();
    }

    @Test
    void gradingFailedAtLeast_countsOnlyFailuresRecordedForThatBodyHash() {
        OssIssue issue = issueWithBody("abc");
        String failedBodyHash = issue.getBodyHash();
        ReflectionTestUtils.setField(issue, "gradingFailures", 3);
        ReflectionTestUtils.setField(issue, "gradingFailureSourceHash", failedBodyHash);

        assertThat(issue.gradingFailedAtLeast(3, failedBodyHash)).isTrue();
        assertThat(issue.gradingFailedAtLeast(2, failedBodyHash)).isTrue();
        assertThat(issue.gradingFailedAtLeast(4, failedBodyHash)).isFalse();
        assertThat(issue.gradingFailedAtLeast(3, OssIssueBodyHash.of("edited body"))).isFalse();
    }

    @Test
    void hasGradingFailures_isTrueOnlyOnceAFailureWasRecorded() {
        OssIssue fresh = issueWithBody("abc");
        OssIssue failedOnce = issueWithBody("abc");
        ReflectionTestUtils.setField(failedOnce, "gradingFailures", 1);
        ReflectionTestUtils.setField(failedOnce, "gradingFailureSourceHash", failedOnce.getBodyHash());

        assertThat(fresh.hasGradingFailures()).isFalse();
        assertThat(failedOnce.hasGradingFailures()).isTrue();
    }

    private OssIssue issueWithBody(String body) {
        return OssIssue.create(repo, 5_611_425_470L, 51_878, "Retry interval is ignored", body, OPENED_AT);
    }

    private OssIssue issueWithTitle(String title) {
        return OssIssue.create(repo, 5_611_425_470L, 51_878, title, "abc", OPENED_AT);
    }
}
