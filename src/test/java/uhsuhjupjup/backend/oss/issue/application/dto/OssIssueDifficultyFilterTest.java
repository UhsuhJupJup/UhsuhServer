package uhsuhjupjup.backend.oss.issue.application.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.EASY;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.HARD;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.MEDIUM;

class OssIssueDifficultyFilterTest {

    @Test
    void fromCodes_oneCode_keepsThatDifficultyOnly() {
        assertThat(OssIssueDifficultyFilter.fromCodes("medium").difficulties()).containsExactly(MEDIUM);
    }

    @Test
    void fromCodes_codesSeparatedByCommas_keepsEachInAnyOrder() {
        assertThat(OssIssueDifficultyFilter.fromCodes("hard,easy").difficulties())
                .containsExactlyInAnyOrder(EASY, HARD);
        assertThat(OssIssueDifficultyFilter.fromCodes("hard,easy"))
                .isEqualTo(OssIssueDifficultyFilter.fromCodes("easy,hard"));
    }

    @Test
    void fromCodes_repeatedCode_countsOnce() {
        assertThat(OssIssueDifficultyFilter.fromCodes("easy,easy,medium,easy").difficulties())
                .containsExactlyInAnyOrder(EASY, MEDIUM);
    }

    @Test
    void fromCodes_allThreeCodes_equalsAll() {
        assertThat(OssIssueDifficultyFilter.fromCodes("medium,hard,easy")).isEqualTo(OssIssueDifficultyFilter.all());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", ",", ",,", "easy,", ",easy", "easy,,medium", "easy,medium,"})
    void fromCodes_emptyCodeBetweenOrAroundCommas_throwsIllegalArgument(String codes) {
        assertThatThrownBy(() -> OssIssueDifficultyFilter.fromCodes(codes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"EASY", "Easy", " easy", "easy ", "easy, medium", "easy ,medium", "normal",
            "easy,normal", "easy;medium", "easy medium", "1"})
    void fromCodes_codeOutsideTheThreeLowercaseCodes_throwsIllegalArgument(String codes) {
        assertThatThrownBy(() -> OssIssueDifficultyFilter.fromCodes(codes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void all_keepsEveryDifficulty() {
        assertThat(OssIssueDifficultyFilter.all().difficulties()).containsExactlyInAnyOrder(OssIssueDifficulty.values());
    }

    @Test
    void newFilter_withoutDifficulty_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OssIssueDifficultyFilter(Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OssIssueDifficultyFilter(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newFilter_copiesTheDifficultiesAndCannotBeChanged() {
        Set<OssIssueDifficulty> given = new HashSet<>(Set.of(EASY));
        OssIssueDifficultyFilter filter = new OssIssueDifficultyFilter(given);

        given.add(HARD);

        assertThat(filter.difficulties()).containsExactly(EASY);
        assertThatThrownBy(() -> filter.difficulties().add(MEDIUM))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void filtersWithTheSameDifficulties_areEqualWhateverTheSetType() {
        assertThat(new OssIssueDifficultyFilter(Set.of(EASY, HARD)))
                .isEqualTo(new OssIssueDifficultyFilter(EnumSet.of(HARD, EASY)))
                .isEqualTo(OssIssueDifficultyFilter.fromCodes("easy,hard"));
    }
}
