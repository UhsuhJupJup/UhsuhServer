package uhsuhjupjup.backend.oss.issue.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OssIssueBodyHashTest {

    private static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void of_givesLowercaseSha256HexOfUtf8Body() {
        assertThat(OssIssueBodyHash.of("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(OssIssueBodyHash.of("재현 방법\r\n1. 설정 파일을 읽는다"))
                .isEqualTo("446646d1c572903a4d2425019239923efecd39ce64e1403262d17758a4fae961");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void of_missingOrEmptyBody_hashesAsEmptyBody(String body) {
        assertThat(OssIssueBodyHash.of(body)).isEqualTo(EMPTY_BODY_SHA256);
    }

    @Test
    void of_lineBreakTrailingSpaceAndOuterBlankLineEdits_keepTheHash() {
        assertThat(OssIssueBodyHash.of("\n \nSteps  \r\n1. run it\t\r2. see it fail\r\n\r\n"))
                .isEqualTo(OssIssueBodyHash.of("Steps\n1. run it\n2. see it fail"));
    }

    @Test
    void of_isTheHashAnIssueKeepsForThatBody() {
        OssRepo repo = OssRepo.create(1296269L, "octocat/Hello-World", null, "Java", 80);
        String body = "Steps  \r\n1. run it";

        assertThat(OssIssue.create(repo, 1L, 1, "Title", body, LocalDateTime.of(2026, 10, 8, 9, 0)).getBodyHash())
                .isEqualTo(OssIssueBodyHash.of(body));
    }
}
