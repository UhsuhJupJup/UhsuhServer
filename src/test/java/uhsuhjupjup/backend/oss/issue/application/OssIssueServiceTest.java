package uhsuhjupjup.backend.oss.issue.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDetailResult;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OssIssueServiceTest {

    private static final Long ISSUE_ID = 501L;
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 5, 9, 12, 44);
    private static final LocalDateTime GRADED_AT = LocalDateTime.of(2026, 10, 8, 14, 20, 11);
    private static final String REASON_KO = "재현 테스트와 원인 함수, 한 줄 수정 제안까지 본문에 있다.";
    private static final String REASON_EN = "The body has a failing test, the faulty function, and a one-line fix.";
    private static final String SUMMARY_KO = "설정을 읽는 순서 때문에 retry.interval이 기본값으로 덮어써진다.";
    private static final String SUMMARY_EN = "Settings are read in an order that resets retry.interval to its default.";

    @Mock
    private OssIssueRepository ossIssueRepository;

    @Mock
    private OssIssueGradeRepository ossIssueGradeRepository;

    @InjectMocks
    private OssIssueService ossIssueService;

    private final OssIssue issue = issue(repo(10L, "acme/fastqueue"));

    @Test
    void getDetail_inKorean_returnsIssueRepoVerdictAndKoreanReasonAndSummary() {
        givenIssueWithCurrentGrade(grade(null));

        OssIssueDetailResult result = ossIssueService.getDetail(ISSUE_ID, OssIssueLanguage.KO);

        assertThat(result).isEqualTo(new OssIssueDetailResult(ISSUE_ID, 1284, "Retry interval is ignored",
                OPENED_AT, 10L, "acme/fastqueue", OssIssueDifficulty.MEDIUM,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL, OssIssueEvidence.ABSENT,
                true, OssIssueLanguage.KO, REASON_KO, SUMMARY_KO, GRADED_AT));
    }

    @Test
    void getDetail_inEnglish_returnsEnglishReasonAndSummaryWithTheSameVerdict() {
        givenIssueWithCurrentGrade(grade(null));

        OssIssueDetailResult result = ossIssueService.getDetail(ISSUE_ID, OssIssueLanguage.EN);

        assertThat(result).isEqualTo(new OssIssueDetailResult(ISSUE_ID, 1284, "Retry interval is ignored",
                OPENED_AT, 10L, "acme/fastqueue", OssIssueDifficulty.MEDIUM,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL, OssIssueEvidence.ABSENT,
                true, OssIssueLanguage.EN, REASON_EN, SUMMARY_EN, GRADED_AT));
    }

    @Test
    void getDetail_withoutLanguage_answersInKorean() {
        givenIssueWithCurrentGrade(grade(null));

        OssIssueDetailResult result = ossIssueService.getDetail(ISSUE_ID, null);

        assertThat(result.language()).isEqualTo(OssIssueLanguage.KO);
        assertThat(result.reason()).isEqualTo(REASON_KO);
        assertThat(result.summary()).isEqualTo(SUMMARY_KO);
    }

    @Test
    void getDetail_missingIssueOrIssueOfSuspendedRepo_throwsIssueNotFoundWithoutReadingGrades() {
        given(ossIssueRepository.findWithRepoByIdAndRepoStatus(ISSUE_ID, OssRepoStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertIssueNotFound(OssIssueLanguage.KO);

        then(ossIssueGradeRepository).shouldHaveNoInteractions();
    }

    @Test
    void getDetail_withoutCurrentGrade_throwsIssueNotFound() {
        given(ossIssueRepository.findWithRepoByIdAndRepoStatus(ISSUE_ID, OssRepoStatus.ACTIVE))
                .willReturn(Optional.of(issue));
        given(ossIssueGradeRepository.findCurrentByIssueId(ISSUE_ID)).willReturn(Optional.empty());

        assertIssueNotFound(OssIssueLanguage.KO);
    }

    @ParameterizedTest
    @EnumSource(OssIssueGradeExclusion.class)
    void getDetail_currentGradeExcluded_throwsIssueNotFoundEvenWithDifficulty(OssIssueGradeExclusion exclusion) {
        givenIssueWithCurrentGrade(grade(exclusion));

        assertIssueNotFound(OssIssueLanguage.EN);
    }

    private void givenIssueWithCurrentGrade(OssIssueGrade grade) {
        given(ossIssueRepository.findWithRepoByIdAndRepoStatus(ISSUE_ID, OssRepoStatus.ACTIVE))
                .willReturn(Optional.of(issue));
        given(ossIssueGradeRepository.findCurrentByIssueId(ISSUE_ID)).willReturn(Optional.of(grade));
    }

    private void assertIssueNotFound(OssIssueLanguage language) {
        assertThatThrownBy(() -> ossIssueService.getDetail(ISSUE_ID, language))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OSS_ISSUE_NOT_FOUND);
    }

    private OssIssueGrade grade(OssIssueGradeExclusion exclusion) {
        OssIssueGrade grade = OssIssueGrade.create(issue, OssIssueDifficulty.MEDIUM,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL, OssIssueEvidence.ABSENT,
                true, exclusion, REASON_KO, REASON_EN, SUMMARY_KO, SUMMARY_EN,
                "v1", "claude-haiku-4-5-20251001", issue.getBodyHash());
        ReflectionTestUtils.setField(grade, "id", 900L);
        ReflectionTestUtils.setField(grade, "createdAt", GRADED_AT);
        return grade;
    }

    private static OssIssue issue(OssRepo repo) {
        OssIssue issue = OssIssue.create(repo, 5_611_425_470L, 1284, "Retry interval is ignored",
                "Steps to reproduce", OPENED_AT);
        ReflectionTestUtils.setField(issue, "id", ISSUE_ID);
        return issue;
    }

    private static OssRepo repo(Long id, String fullName) {
        OssRepo repo = OssRepo.create(id, fullName, null, "Go", 12_000);
        ReflectionTestUtils.setField(repo, "id", id);
        return repo;
    }
}
