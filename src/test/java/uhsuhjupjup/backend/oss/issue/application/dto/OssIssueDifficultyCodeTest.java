package uhsuhjupjup.backend.oss.issue.application.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssIssueDifficultyCodeTest {

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({"easy, EASY", "medium, MEDIUM", "hard, HARD"})
    void fromCode_lowercaseCode_mapsToTheDomainDifficulty(String code, OssIssueDifficulty expected) {
        assertThat(OssIssueDifficultyCode.fromCode(code).difficulty()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "EASY", "Easy", " easy", "easy ", "normal", "1", "easy,medium"})
    void fromCode_anythingElse_throwsIllegalArgument(String code) {
        assertThatThrownBy(() -> OssIssueDifficultyCode.fromCode(code))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromCode_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> OssIssueDifficultyCode.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyDomainDifficulty_hasExactlyOneCode() {
        assertThat(Arrays.stream(OssIssueDifficultyCode.values()).map(OssIssueDifficultyCode::difficulty))
                .containsExactlyInAnyOrder(OssIssueDifficulty.values());
    }
}
