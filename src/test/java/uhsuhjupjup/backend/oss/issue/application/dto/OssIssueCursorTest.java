package uhsuhjupjup.backend.oss.issue.application.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssIssueCursorTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 5, 9, 12, 44);

    @Test
    void encodeThenDecode_restoresTheCursor() {
        OssIssueCursor cursor = new OssIssueCursor(OPENED_AT, 501L);

        assertThat(OssIssueCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void encode_writesIdAndSecondsEvenWhenTheyAreZero() {
        OssIssueCursor cursor = new OssIssueCursor(LocalDateTime.of(2026, 10, 5, 9, 12, 0), 7L);

        assertThat(payloadOf(cursor.encode())).isEqualTo("7:2026-10-05T09:12:00");
        assertThat(OssIssueCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @ParameterizedTest(name = "[{index}] {0}, {1}")
    @CsvSource({"1, 2026-10-05T09:12:44", "9223372036854775807, 9999-12-31T23:59:59", "62, 1000-01-01T00:00:00",
            "1023, 2026-01-31T23:59:59"})
    void encode_usesUrlSafeAlphabetWithoutPaddingAndRoundTrips(long id, LocalDateTime openedAt) {
        OssIssueCursor cursor = new OssIssueCursor(openedAt, id);

        assertThat(cursor.encode()).matches("[A-Za-z0-9_-]+");
        assertThat(OssIssueCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void after_takesTheGithubCreationTimeAndIdOfTheIssue() {
        OssIssue issue = OssIssue.create(OssRepo.create(1L, "acme/fastqueue", null, "Go", 12_000),
                5_611_425_470L, 1284, "Retry interval is ignored", "Steps to reproduce", OPENED_AT);
        ReflectionTestUtils.setField(issue, "id", 501L);

        assertThat(OssIssueCursor.after(issue)).isEqualTo(new OssIssueCursor(OPENED_AT, 501L));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"!!!", "a", "NTAx OjIw", "NTAxOjIwMjYtMTAtMDVUMDk6MTI6NDQ=?", "NTAx+jIw", "NTAx/jIw"})
    void decode_notBase64Url_throwsIllegalArgument(String token) {
        assertThatThrownBy(() -> OssIssueCursor.decode(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
            "",
            "501",
            "501:",
            ":2026-10-05T09:12:44",
            "abc:2026-10-05T09:12:44",
            "0:2026-10-05T09:12:44",
            "-1:2026-10-05T09:12:44",
            "99999999999999999999:2026-10-05T09:12:44",
            "501:2026-10-05",
            "501:2026-10-05T09:12",
            "501:2026-10-05t09:12:44",
            "501:2026-10-05 09:12:44",
            "501:2026-10-05T09:12:44Z",
            "501:2026-10-05T09:12:44+09:00",
            "501:2026-02-30T09:12:44",
            "501:2026-10-05T24:00:00",
            "501:0999-12-31T23:59:59",
            "501:+10000-01-01T00:00:00",
            "501:yesterday",
            "STARS:10:80000",
            "NAME:10:acme/fastqueue"
    })
    void decode_malformedPayload_throwsIllegalArgument(String payload) {
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> OssIssueCursor.decode(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
            "498:2026-10-05T09:12:44.5",
            "498:2026-10-05T09:12:44.0",
            "498:2026-10-05T09:12:44.000",
            "498:2026-10-05T09:12:44.123456789"
    })
    void decode_fractionOfASecond_throwsIllegalArgumentEvenWhenItIsZero(String payload) {
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> OssIssueCursor.decode(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newCursor_timeWithAFractionOfASecond_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OssIssueCursor(OPENED_AT.withNano(500_000_000), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OssIssueCursor(OPENED_AT.withNano(1), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newCursor_withoutTimeOrWithIdBelowOne_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OssIssueCursor(null, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OssIssueCursor(OPENED_AT, 0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newCursor_timeOutsideTheDatetimeColumnRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OssIssueCursor(LocalDateTime.of(999, 12, 31, 23, 59, 59), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OssIssueCursor(LocalDateTime.of(10_000, 1, 1, 0, 0), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String payloadOf(String token) {
        return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }
}
