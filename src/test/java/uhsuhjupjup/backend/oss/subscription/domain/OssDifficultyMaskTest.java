package uhsuhjupjup.backend.oss.subscription.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.EASY;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.HARD;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.MEDIUM;

class OssDifficultyMaskTest {

    static Stream<Arguments> everySelection() {
        return Stream.of(
                Arguments.of(Set.of(EASY), 1),
                Arguments.of(Set.of(MEDIUM), 2),
                Arguments.of(Set.of(EASY, MEDIUM), 3),
                Arguments.of(Set.of(HARD), 4),
                Arguments.of(Set.of(EASY, HARD), 5),
                Arguments.of(Set.of(MEDIUM, HARD), 6),
                Arguments.of(Set.of(EASY, MEDIUM, HARD), 7));
    }

    @ParameterizedTest
    @MethodSource("everySelection")
    void of_everySelection_isItsMask(Set<OssIssueDifficulty> difficulties, int mask) {
        assertThat(OssDifficultyMask.of(difficulties)).isEqualTo(mask);
    }

    @ParameterizedTest
    @MethodSource("everySelection")
    void difficultiesOf_everyMask_isItsSelection(Set<OssIssueDifficulty> difficulties, int mask) {
        assertThat(OssDifficultyMask.difficultiesOf(mask)).isEqualTo(difficulties);
    }

    @ParameterizedTest
    @CsvSource({"EASY, 1", "MEDIUM, 2", "HARD, 4"})
    void bitOf_eachDifficulty_hasItsFixedBit(OssIssueDifficulty difficulty, int bit) {
        assertThat(OssDifficultyMask.bitOf(difficulty)).isEqualTo(bit);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void of_noDifficulty_isRejected(Set<OssIssueDifficulty> difficulties) {
        assertThatThrownBy(() -> OssDifficultyMask.of(difficulties))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void of_nullAmongDifficulties_isRejected() {
        Set<OssIssueDifficulty> withNull = new HashSet<>(Arrays.asList(EASY, null));

        assertThatThrownBy(() -> OssDifficultyMask.of(withNull))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, 9, 15, -1, Integer.MIN_VALUE})
    void difficultiesOf_brokenMask_isRejected(int mask) {
        assertThatThrownBy(() -> OssDifficultyMask.difficultiesOf(mask))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }
}
