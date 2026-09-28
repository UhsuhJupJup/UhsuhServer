package uhsuhjupjup.backend.oss.repo.application.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssRepoCursorTest {

    @Test
    void encodeThenDecode_restoresStarsCursor() {
        OssRepoCursor cursor = new OssRepoCursor(OssRepoSort.STARS, "80000", 10L);

        assertThat(OssRepoCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void encodeThenDecode_restoresNameCursor() {
        OssRepoCursor cursor = new OssRepoCursor(OssRepoSort.NAME, "spring-projects/spring-boot", 10L);

        assertThat(OssRepoCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void encodeThenDecode_keepsSeparatorInsideNameKey() {
        OssRepoCursor cursor = new OssRepoCursor(OssRepoSort.NAME, "octocat/a:b:c", 3L);

        assertThat(OssRepoCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"octocat/???", "a>>b/c~~~"})
    void encode_usesUrlSafeAlphabetWithoutPadding(String key) {
        String token = new OssRepoCursor(OssRepoSort.NAME, key, 7L).encode();

        assertThat(token).matches("[A-Za-z0-9_-]+");
        assertThat(OssRepoCursor.decode(token).key()).isEqualTo(key);
    }

    @Test
    void after_starsSort_takesStarsAndIdOfRepo() {
        OssRepo repo = repo(25L, "Facebook/React", 230_000);

        assertThat(OssRepoCursor.after(OssRepoSort.STARS, repo))
                .isEqualTo(new OssRepoCursor(OssRepoSort.STARS, "230000", 25L));
    }

    @Test
    void after_nameSort_takesLowercaseNameAndIdOfRepo() {
        OssRepo repo = repo(25L, "Facebook/React", 230_000);

        assertThat(OssRepoCursor.after(OssRepoSort.NAME, repo))
                .isEqualTo(new OssRepoCursor(OssRepoSort.NAME, "facebook/react", 25L));
    }

    @Test
    void stars_readsStarsKey() {
        assertThat(new OssRepoCursor(OssRepoSort.STARS, "300", 1L).stars()).isEqualTo(300);
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!!", "U1RBUlM6MTA6ODAwMDA=?", "a", "U1RB UlM6"})
    void decode_notBase64Url_throwsIllegalArgument(String token) {
        assertThatThrownBy(() -> OssRepoCursor.decode(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "STARS",
            "STARS:10",
            "stars:10:80000",
            "POPULAR:10:80000",
            "STARS:ten:80000",
            "STARS::80000",
            "STARS:0:80000",
            "STARS:-1:80000",
            "STARS:10:many",
            "STARS:10:-5",
            "STARS:10:99999999999",
            "STARS:10:",
            "NAME:10:"
    })
    void decode_malformedPayload_throwsIllegalArgument(String payload) {
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> OssRepoCursor.decode(token)).isInstanceOf(IllegalArgumentException.class);
    }

    private static OssRepo repo(Long id, String fullName, int stars) {
        OssRepo repo = OssRepo.create(1L, fullName, null, "JavaScript", stars);
        ReflectionTestUtils.setField(repo, "id", id);
        return repo;
    }
}
