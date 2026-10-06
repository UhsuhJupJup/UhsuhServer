package uhsuhjupjup.backend.oss.issue.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssIssueGradeTest {

    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final OssIssue issue = OssIssue.create(
            OssRepo.create(6296790L, "spring-projects/spring-boot", null, "Java", 80_000),
            5_611_425_470L, 51_878, "Retry interval is ignored", "abc", LocalDateTime.of(2026, 9, 28, 17, 11, 25));

    @Test
    void create_keepsVerdictAndWhatItWasGradedFrom() {
        OssIssueGrade grade = OssIssueGrade.create(issue, OssIssueDifficulty.MEDIUM,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL, OssIssueEvidence.ABSENT, OssIssueEvidence.PRESENT,
                true, null,
                "재현 절차는 있지만 원인이 없다.", "Steps are given but the cause is missing.",
                "종료 훅 순서 때문에 워커가 남는다.", "Workers linger because of the shutdown hook order.",
                "v1", "claude-haiku-4-5-20251001", EMPTY_BODY_SHA256);

        assertThat(grade.getIssue()).isSameAs(issue);
        assertThat(grade.getDifficulty()).isEqualTo(OssIssueDifficulty.MEDIUM);
        assertThat(grade.getProblem()).isEqualTo(OssIssueEvidence.PRESENT);
        assertThat(grade.getReproduction()).isEqualTo(OssIssueEvidence.PARTIAL);
        assertThat(grade.getCause()).isEqualTo(OssIssueEvidence.ABSENT);
        assertThat(grade.getFixDirection()).isEqualTo(OssIssueEvidence.PRESENT);
        assertThat(grade.isRelatedPr()).isTrue();
        assertThat(grade.getExclusion()).isNull();
        assertThat(grade.getReasonKo()).isEqualTo("재현 절차는 있지만 원인이 없다.");
        assertThat(grade.getReasonEn()).isEqualTo("Steps are given but the cause is missing.");
        assertThat(grade.getSummaryKo()).isEqualTo("종료 훅 순서 때문에 워커가 남는다.");
        assertThat(grade.getSummaryEn()).isEqualTo("Workers linger because of the shutdown hook order.");
        assertThat(grade.getCriteriaVersion()).isEqualTo("v1");
        assertThat(grade.getModel()).isEqualTo("claude-haiku-4-5-20251001");
        assertThat(grade.getSourceHash()).isEqualTo(EMPTY_BODY_SHA256).isNotEqualTo(issue.getBodyHash());
    }

    @Test
    void create_neitherDifficultyNorExclusion_isRejected() {
        assertThatThrownBy(() -> gradeOf(null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @ParameterizedTest
    @CsvSource(value = {"요약, null", "null, Summary.", "null, null"}, nullValues = "null")
    void create_notExcludedMissingEitherSummary_isRejected(String summaryKo, String summaryEn) {
        assertThatThrownBy(() -> gradeOf(OssIssueDifficulty.EASY, null, summaryKo, summaryEn))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @ParameterizedTest
    @CsvSource(value = {"요약, null", "null, Summary."}, nullValues = "null")
    void create_excludedWithSummaryInOnlyOneLanguage_isRejected(String summaryKo, String summaryEn) {
        assertThatThrownBy(() -> gradeOf(null, OssIssueGradeExclusion.SPAM, summaryKo, summaryEn))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @ParameterizedTest
    @EnumSource(OssIssueGradeExclusion.class)
    void create_excludedWithoutDifficulty_isAllowed(OssIssueGradeExclusion exclusion) {
        OssIssueGrade grade = gradeOf(null, exclusion);

        assertThat(grade.getDifficulty()).isNull();
        assertThat(grade.getExclusion()).isEqualTo(exclusion);
    }

    @Test
    void create_excludedWithoutSummary_isAllowed() {
        OssIssueGrade grade = gradeOf(null, OssIssueGradeExclusion.SPAM, null, null);

        assertThat(grade.getSummaryKo()).isNull();
        assertThat(grade.getSummaryEn()).isNull();
    }

    @Test
    void create_excludedWithDifficulty_keepsBoth() {
        OssIssueGrade grade = gradeOf(OssIssueDifficulty.EASY, OssIssueGradeExclusion.DUPLICATE);

        assertThat(grade.getDifficulty()).isEqualTo(OssIssueDifficulty.EASY);
        assertThat(grade.getExclusion()).isEqualTo(OssIssueGradeExclusion.DUPLICATE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"claude-haiku-4-5-20251001",
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015a"})
    void create_sourceHashNotLowercaseSha256Hex_isRejected(String sourceHash) {
        assertThatThrownBy(() -> gradeWithSourceHash(sourceHash))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @ParameterizedTest
    @ValueSource(strings = {ABC_SHA256, EMPTY_BODY_SHA256})
    void create_lowercaseSha256Hex_isKeptAsSourceHash(String sourceHash) {
        assertThat(gradeWithSourceHash(sourceHash).getSourceHash()).isEqualTo(sourceHash);
    }

    private OssIssueGrade gradeOf(OssIssueDifficulty difficulty, OssIssueGradeExclusion exclusion) {
        return gradeOf(difficulty, exclusion, "요약", "Summary.");
    }

    private OssIssueGrade gradeOf(OssIssueDifficulty difficulty, OssIssueGradeExclusion exclusion,
                                  String summaryKo, String summaryEn) {
        return OssIssueGrade.create(issue, difficulty,
                OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT,
                false, exclusion,
                "이유", "Reason.", summaryKo, summaryEn,
                "v1", "claude-haiku-4-5", ABC_SHA256);
    }

    private OssIssueGrade gradeWithSourceHash(String sourceHash) {
        return OssIssueGrade.create(issue, OssIssueDifficulty.EASY,
                OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT,
                false, null,
                "이유", "Reason.", "요약", "Summary.",
                "v1", "claude-haiku-4-5", sourceHash);
    }
}
