package uhsuhjupjup.backend.oss.issue.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
    void create_hashesBodyAsUtf8BytesWithoutNormalizingLineBreaks() {
        assertThat(issueWithBody("재현 방법\r\n1. 설정 파일을 읽는다").getBodyHash())
                .isEqualTo("50628277c49c449f2952684d99502d5188aa93b3b0e10d63361101ca5ebd931e");
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

    private OssIssue issueWithBody(String body) {
        return OssIssue.create(repo, 5_611_425_470L, 51_878, "Retry interval is ignored", body, OPENED_AT);
    }

    private OssIssue issueWithTitle(String title) {
        return OssIssue.create(repo, 5_611_425_470L, 51_878, title, "abc", OPENED_AT);
    }
}
