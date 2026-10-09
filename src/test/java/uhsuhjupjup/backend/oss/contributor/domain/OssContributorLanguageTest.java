package uhsuhjupjup.backend.oss.contributor.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssContributorLanguageTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"ko, KO", "en, EN"})
    void fromCode_lowercaseCode_findsTheLanguage(String code, OssContributorLanguage expected) {
        assertThat(OssContributorLanguage.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(OssContributorLanguage.class)
    void code_isReadBackAsTheSameLanguage(OssContributorLanguage language) {
        assertThat(OssContributorLanguage.fromCode(language.code())).isSameAs(language);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"KO", "En", "jp", "kor", "ko-KR", "", " ", " ko", "en "})
    void fromCode_codeOutsideTwoLowercaseCodes_throws(String code) {
        assertThatThrownBy(() -> OssContributorLanguage.fromCode(code))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromCode_null_throws() {
        assertThatThrownBy(() -> OssContributorLanguage.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
