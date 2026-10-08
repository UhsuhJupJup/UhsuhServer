package uhsuhjupjup.backend.oss.issue.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueCursor;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDetailResult;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDifficultyFilter;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssuePageResult;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueResult;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OssIssueServiceTest {

    private static final Long ISSUE_ID = 501L;
    private static final Long REPO_ID = 10L;
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 5, 9, 12, 44);
    private static final LocalDateTime GRADED_AT = LocalDateTime.of(2026, 10, 8, 14, 20, 11);
    private static final String REASON_KO = "재현 테스트와 원인 함수, 한 줄 수정 제안까지 본문에 있다.";
    private static final String REASON_EN = "The body has a failing test, the faulty function, and a one-line fix.";
    private static final String SUMMARY_KO = "설정을 읽는 순서 때문에 retry.interval이 기본값으로 덮어써진다.";
    private static final String SUMMARY_EN = "Settings are read in an order that resets retry.interval to its default.";
    private static final Set<OssIssueDifficulty> EVERY_DIFFICULTY = EnumSet.allOf(OssIssueDifficulty.class);
    private static final Limit DEFAULT_PAGE_AND_ONE = Limit.of(21);

    @Mock
    private OssIssueRepository ossIssueRepository;

    @Mock
    private OssIssueGradeRepository ossIssueGradeRepository;

    @Mock
    private OssRepoRepository ossRepoRepository;

    @InjectMocks
    private OssIssueService ossIssueService;

    private final OssIssue issue = issue(repo(10L, "acme/fastqueue"));

    private final OssRepo fastqueue = repo(REPO_ID, "acme/fastqueue");

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

    @Test
    void getRepoIssues_withoutParameters_listsCurrentGradesOfEveryDifficultyInKoreanWithDefaultSize() {
        OssIssueGrade newest = listedGrade(issue(fastqueue, 601L, 1300, OPENED_AT.plusHours(2)), OssIssueDifficulty.EASY);
        OssIssueGrade older = listedGrade(issue(fastqueue, 501L, 1284, OPENED_AT), OssIssueDifficulty.HARD);
        givenActiveRepo();
        given(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(REPO_ID, EVERY_DIFFICULTY, null, null,
                DEFAULT_PAGE_AND_ONE)).willReturn(List.of(newest, older));

        OssIssuePageResult result = ossIssueService.getRepoIssues(REPO_ID, null, null, null, null);

        assertThat(result.language()).isEqualTo(OssIssueLanguage.KO);
        assertThat(result.items()).containsExactly(
                new OssIssueResult(601L, 1300, "Issue 1300", OPENED_AT.plusHours(2), "acme/fastqueue",
                        OssIssueDifficulty.EASY, OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL,
                        OssIssueEvidence.ABSENT, OssIssueEvidence.PRESENT, true, SUMMARY_KO, GRADED_AT),
                new OssIssueResult(501L, 1284, "Issue 1284", OPENED_AT, "acme/fastqueue",
                        OssIssueDifficulty.HARD, OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL,
                        OssIssueEvidence.ABSENT, OssIssueEvidence.PRESENT, true, SUMMARY_KO, GRADED_AT));
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void getRepoIssues_inEnglish_choosesTheEnglishSummaryAndSaysSo() {
        OssIssueGrade grade = listedGrade(issue(fastqueue, 501L, 1284, OPENED_AT), OssIssueDifficulty.MEDIUM);
        givenActiveRepo();
        given(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(REPO_ID, EVERY_DIFFICULTY, null, null,
                DEFAULT_PAGE_AND_ONE)).willReturn(List.of(grade));

        OssIssuePageResult result = ossIssueService.getRepoIssues(REPO_ID, null, OssIssueLanguage.EN, null, null);

        assertThat(result.language()).isEqualTo(OssIssueLanguage.EN);
        assertThat(result.items()).extracting(OssIssueResult::summary).containsExactly(SUMMARY_EN);
    }

    @Test
    void getRepoIssues_difficultyFilter_readsOnlyThoseDifficulties() {
        givenActiveRepo();
        given(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(REPO_ID,
                EnumSet.of(OssIssueDifficulty.EASY, OssIssueDifficulty.HARD), null, null, DEFAULT_PAGE_AND_ONE))
                .willReturn(List.of());

        OssIssuePageResult result = ossIssueService.getRepoIssues(REPO_ID,
                OssIssueDifficultyFilter.fromCodes("hard,easy"), null, null, null);

        assertThat(result.items()).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] size {0} -> {1}")
    @CsvSource(value = {"null, 20", "0, 20", "-5, 20", "1, 1", "20, 20", "50, 50", "51, 50", "1000, 50"},
            nullValues = "null")
    void getRepoIssues_size_isClampedBetweenOneAndFifty(Integer size, int expected) {
        givenActiveRepo();
        given(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(REPO_ID, EVERY_DIFFICULTY, null, null,
                Limit.of(expected + 1))).willReturn(List.of());

        ossIssueService.getRepoIssues(REPO_ID, null, null, null, size);

        then(ossIssueGradeRepository).should()
                .findCurrentNotExcludedByRepoId(REPO_ID, EVERY_DIFFICULTY, null, null, Limit.of(expected + 1));
    }

    @Test
    void getRepoIssues_moreThanPageSize_returnsThePageAndACursorAfterItsLastIssue() {
        OssIssueGrade first = listedGrade(issue(fastqueue, 603L, 3, OPENED_AT.plusHours(2)), OssIssueDifficulty.EASY);
        OssIssueGrade second = listedGrade(issue(fastqueue, 602L, 2, OPENED_AT), OssIssueDifficulty.EASY);
        OssIssueGrade beyond = listedGrade(issue(fastqueue, 601L, 1, OPENED_AT), OssIssueDifficulty.EASY);
        givenActiveRepo();
        given(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(REPO_ID, EVERY_DIFFICULTY, null, null,
                Limit.of(3))).willReturn(List.of(first, second, beyond));

        OssIssuePageResult result = ossIssueService.getRepoIssues(REPO_ID, null, null, null, 2);

        assertThat(result.items()).extracting(OssIssueResult::id).containsExactly(603L, 602L);
        assertThat(result.nextCursor()).isEqualTo(new OssIssueCursor(OPENED_AT, 602L));
    }

    @Test
    void getRepoIssues_exactlyPageSize_returnsNoCursor() {
        OssIssueGrade first = listedGrade(issue(fastqueue, 602L, 2, OPENED_AT), OssIssueDifficulty.EASY);
        OssIssueGrade second = listedGrade(issue(fastqueue, 601L, 1, OPENED_AT), OssIssueDifficulty.EASY);
        givenActiveRepo();
        given(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(REPO_ID, EVERY_DIFFICULTY, null, null,
                Limit.of(3))).willReturn(List.of(first, second));

        OssIssuePageResult result = ossIssueService.getRepoIssues(REPO_ID, null, null, null, 2);

        assertThat(result.items()).hasSize(2);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void getRepoIssues_cursor_readsAfterItsGithubCreationTimeAndId() {
        OssIssueGrade next = listedGrade(issue(fastqueue, 601L, 1, OPENED_AT.minusDays(1)), OssIssueDifficulty.MEDIUM);
        givenActiveRepo();
        given(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(REPO_ID, Set.of(OssIssueDifficulty.MEDIUM),
                OPENED_AT, 602L, DEFAULT_PAGE_AND_ONE)).willReturn(List.of(next));

        OssIssuePageResult result = ossIssueService.getRepoIssues(REPO_ID,
                OssIssueDifficultyFilter.fromCodes("medium"), null, new OssIssueCursor(OPENED_AT, 602L), null);

        assertThat(result.items()).extracting(OssIssueResult::id).containsExactly(601L);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void getRepoIssues_missingOrSuspendedRepo_throwsRepoNotFoundWithoutReadingIssues() {
        given(ossRepoRepository.findByIdAndStatus(REPO_ID, OssRepoStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ossIssueService.getRepoIssues(REPO_ID,
                OssIssueDifficultyFilter.fromCodes("easy"), OssIssueLanguage.EN, new OssIssueCursor(OPENED_AT, 1L), 5))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OSS_REPO_NOT_FOUND);

        then(ossIssueGradeRepository).shouldHaveNoInteractions();
        then(ossIssueRepository).shouldHaveNoInteractions();
    }

    @Test
    void getRepoIssues_activeRepoWithNothingToList_returnsAnEmptyPageInTheChosenLanguage() {
        givenActiveRepo();
        given(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(REPO_ID, EVERY_DIFFICULTY, null, null,
                DEFAULT_PAGE_AND_ONE)).willReturn(List.of());

        OssIssuePageResult result = ossIssueService.getRepoIssues(REPO_ID, null, OssIssueLanguage.EN, null, null);

        assertThat(result).isEqualTo(new OssIssuePageResult(OssIssueLanguage.EN, List.of(), null));
    }

    private void givenIssueWithCurrentGrade(OssIssueGrade grade) {
        given(ossIssueRepository.findWithRepoByIdAndRepoStatus(ISSUE_ID, OssRepoStatus.ACTIVE))
                .willReturn(Optional.of(issue));
        given(ossIssueGradeRepository.findCurrentByIssueId(ISSUE_ID)).willReturn(Optional.of(grade));
    }

    private void givenActiveRepo() {
        given(ossRepoRepository.findByIdAndStatus(REPO_ID, OssRepoStatus.ACTIVE)).willReturn(Optional.of(fastqueue));
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

    private static OssIssueGrade listedGrade(OssIssue listed, OssIssueDifficulty difficulty) {
        OssIssueGrade grade = OssIssueGrade.create(listed, difficulty,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL, OssIssueEvidence.ABSENT, OssIssueEvidence.PRESENT,
                true, null, REASON_KO, REASON_EN, SUMMARY_KO, SUMMARY_EN,
                "v1", "claude-haiku-4-5-20251001", listed.getBodyHash());
        ReflectionTestUtils.setField(grade, "id", listed.getId() + 1000);
        ReflectionTestUtils.setField(grade, "createdAt", GRADED_AT);
        return grade;
    }

    private static OssIssue issue(OssRepo repo) {
        OssIssue issue = OssIssue.create(repo, 5_611_425_470L, 1284, "Retry interval is ignored",
                "Steps to reproduce", OPENED_AT);
        ReflectionTestUtils.setField(issue, "id", ISSUE_ID);
        return issue;
    }

    private static OssIssue issue(OssRepo repo, Long id, int number, LocalDateTime openedAt) {
        OssIssue issue = OssIssue.create(repo, 9_000_000_000L + id, number, "Issue " + number,
                "Body of issue " + number, openedAt);
        ReflectionTestUtils.setField(issue, "id", id);
        return issue;
    }

    private static OssRepo repo(Long id, String fullName) {
        OssRepo repo = OssRepo.create(id, fullName, null, "Go", 12_000);
        ReflectionTestUtils.setField(repo, "id", id);
        return repo;
    }
}
