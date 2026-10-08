package uhsuhjupjup.backend.oss.pipeline.grading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueDetail;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueLookup;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueBodyHash;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGrader;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.OssIssueGradeSaver;
import uhsuhjupjup.backend.oss.pipeline.grading.application.OssIssueGradingService;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult.StopReason;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueUngradableReason;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class OssIssueGradingIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 9, 0, 0);
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 7, 12, 0, 0);
    private static final String GRADING_LOCK_PREFIX = "ossIssueGrade-";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String BODY = "Steps to reproduce\n1. run it";
    private static final String EDITED_BODY = "Steps to reproduce\n1. run it twice";
    private static final OssIssueVerdict VERDICT = new OssIssueVerdict(OssIssueDifficulty.MEDIUM,
            OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.ABSENT, OssIssueEvidence.PARTIAL,
            false, null, "재현 절차는 있지만 원인이 없다.", "Steps are given but the cause is missing.",
            "종료 뒤 워커가 남는다.", "Workers linger after shutdown.");
    private static final IssueGradingResult GRADED = new IssueGradingResult(VERDICT, MODEL);

    @Autowired
    private OssIssueGradingService ossIssueGradingService;

    @Autowired
    private OssIssueGradeSaver ossIssueGradeSaver;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private OssIssueGradeRepository ossIssueGradeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockitoBean
    private GitHubClient gitHubClient;

    @MockitoBean
    private IssueGrader issueGrader;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private OssIssueRepository ossIssueRepository;

    @BeforeEach
    void setUp() {
        ossRepoRepository.deleteAllInBatch();
        given(clock.instant()).willReturn(NOW.atZone(KST).toInstant());
        given(clock.getZone()).willReturn(KST);
    }

    @Test
    void gradeRepo_savesGradeWithCriteriaVersionAnsweredModelAndFetchedBodyHashCallingOutOfTransactions() {
        OssRepo repo = saveRepo();
        OssIssue issue = saveIssue(repo, 1, BODY, OPENED_AT);
        AtomicBoolean fetchedInTransaction = new AtomicBoolean(true);
        AtomicBoolean gradedInTransaction = new AtomicBoolean(true);
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willAnswer(invocation -> {
            fetchedInTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return found(1, "Steps to reproduce  \r\n1. run it\r\n");
        });
        given(issueGrader.grade("Fetched title 1", "Steps to reproduce  \r\n1. run it\r\n", List.of("bug")))
                .willAnswer(invocation -> {
                    gradedInTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return GRADED;
                });

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(result).isEqualTo(new OssIssueGradingRunResult(StopReason.COMPLETED, 1, 1, 0, 0, 0, 0, 0,
                Map.of(), Map.of()));
        assertThat(fetchedInTransaction).isFalse();
        assertThat(gradedInTransaction).isFalse();
        assertThat(gradeRows(issue.getId())).containsExactly(new GradeRow("v1", MODEL, OssIssueBodyHash.of(BODY),
                "MEDIUM", "재현 절차는 있지만 원인이 없다.", "Workers linger after shutdown."));
        assertThat(ossIssueGradeRepository.findCurrentByIssueId(issue.getId())).isPresent();
        assertThat(gradingFailures(issue.getId())).isEqualTo(new GradingFailures(0, null));
        assertThat(storedIssue(issue.getId())).isEqualTo(new StoredIssue("Stored title 1", OssIssueBodyHash.of(BODY)));
    }

    @Test
    void gradeRepo_bodyEditedBeforeSyncCaughtUp_gradesTheFetchedBodyAndSkipsItUntilSyncCatchesUp() {
        OssRepo repo = saveRepo();
        OssIssue issue = saveIssue(repo, 1, BODY, OPENED_AT);
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willReturn(found(1, EDITED_BODY));
        given(issueGrader.grade(any(), any(), any())).willReturn(GRADED);

        OssIssueGradingRunResult first = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(first.graded()).isEqualTo(1);
        assertThat(gradeRows(issue.getId())).extracting(GradeRow::sourceHash)
                .containsExactly(OssIssueBodyHash.of(EDITED_BODY));
        assertThat(storedIssue(issue.getId())).isEqualTo(new StoredIssue("Stored title 1", OssIssueBodyHash.of(BODY)));
        assertThat(ossIssueGradeRepository.findCurrentByIssueId(issue.getId())).isEmpty();

        OssIssueGradingRunResult beforeSync = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(beforeSync).isEqualTo(new OssIssueGradingRunResult(StopReason.COMPLETED, 1, 0, 0, 0, 1, 0, 0,
                Map.of(), Map.of()));
        then(issueGrader).should(times(1)).grade(any(), any(), any());

        syncChangesBody(issue.getId(), EDITED_BODY);

        assertThat(ossIssueGradeRepository.findCurrentByIssueId(issue.getId())).isPresent();
        assertThat(ossIssueGradingService.gradeRepo(repo.getId()).selected()).isZero();
    }

    @Test
    void currentGrade_bodyChangedBySync_disappearsAndTheNewBodyIsGradedInTheNextRun() {
        OssRepo repo = saveRepo();
        OssIssue issue = saveIssue(repo, 1, BODY, OPENED_AT);
        given(gitHubClient.findIssue(repo.getGithubId(), 1))
                .willReturn(found(1, BODY))
                .willReturn(found(1, EDITED_BODY));
        given(issueGrader.grade(any(), any(), any())).willReturn(GRADED);
        ossIssueGradingService.gradeRepo(repo.getId());
        Long gradeOfOldBody = ossIssueGradeRepository.findCurrentByIssueId(issue.getId()).orElseThrow().getId();

        syncChangesBody(issue.getId(), EDITED_BODY);

        assertThat(ossIssueGradeRepository.findCurrentByIssueId(issue.getId())).isEmpty();

        OssIssueGradingRunResult regraded = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(regraded.graded()).isEqualTo(1);
        assertThat(ossIssueGradeRepository.findCurrentByIssueId(issue.getId())).get().satisfies(current -> {
            assertThat(current.getId()).isGreaterThan(gradeOfOldBody);
            assertThat(current.getSourceHash()).isEqualTo(OssIssueBodyHash.of(EDITED_BODY));
        });
        assertThat(gradeRows(issue.getId())).extracting(GradeRow::sourceHash)
                .containsExactly(OssIssueBodyHash.of(BODY), OssIssueBodyHash.of(EDITED_BODY));
    }

    @Test
    void gradeRepo_sameBodyFailsThreeTimes_isNotSelectedAgainUntilSyncSeesANewBody() {
        OssRepo repo = saveRepo();
        OssIssue issue = saveIssue(repo, 1, BODY, OPENED_AT);
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willReturn(found(1, BODY));
        given(issueGrader.grade(any(), any(), any()))
                .willThrow(new IssueGradingException(Reason.INVALID_OUTPUT, "Claude 판정 출력이 규칙을 어겼습니다"));

        for (int run = 1; run <= 3; run++) {
            assertThat(ossIssueGradingService.gradeRepo(repo.getId()).failed())
                    .containsEntry(Reason.INVALID_OUTPUT, 1);
            assertThat(gradingFailures(issue.getId())).isEqualTo(new GradingFailures(run, OssIssueBodyHash.of(BODY)));
        }

        assertThat(ossIssueGradingService.gradeRepo(repo.getId()).selected()).isZero();
        then(gitHubClient).should(times(3)).findIssue(repo.getGithubId(), 1);

        syncChangesBody(issue.getId(), EDITED_BODY);

        assertThat(gradingFailures(issue.getId())).isEqualTo(new GradingFailures(3, OssIssueBodyHash.of(BODY)));
        willReturn(found(1, EDITED_BODY)).given(gitHubClient).findIssue(repo.getGithubId(), 1);
        willReturn(GRADED).given(issueGrader).grade(any(), any(), any());

        OssIssueGradingRunResult afterEdit = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(afterEdit.graded()).isEqualTo(1);
        assertThat(gradingFailures(issue.getId())).isEqualTo(new GradingFailures(0, null));
    }

    @Test
    void gradeRepo_closedIssue_countsOnTheStoredBodyHashAndIsDroppedAfterThreeRuns() {
        OssRepo repo = saveRepo();
        OssIssue issue = saveIssue(repo, 1, BODY, OPENED_AT);
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willReturn(GitHubIssueLookup.found(
                new GitHubIssueDetail(1_001L, "Fetched title 1", EDITED_BODY, List.of(), false, false, 0, "octocat",
                        "User")));

        for (int run = 1; run <= 3; run++) {
            assertThat(ossIssueGradingService.gradeRepo(repo.getId()).ungradable())
                    .containsEntry(OssIssueUngradableReason.CLOSED, 1);
        }

        assertThat(gradingFailures(issue.getId())).isEqualTo(new GradingFailures(3, OssIssueBodyHash.of(BODY)));
        assertThat(ossIssueGradingService.gradeRepo(repo.getId()).selected()).isZero();
        then(issueGrader).shouldHaveNoInteractions();
    }

    @Test
    void gradeRepo_githubRejectsOneIssueAndFailsOnceForAnother_countsTheRejectionAndSkipsTheOutage() {
        OssRepo repo = saveRepo();
        OssIssue rejected = saveIssue(repo, 3, BODY, OPENED_AT.plusMinutes(3));
        OssIssue skippedByOutage = saveIssue(repo, 2, BODY, OPENED_AT.plusMinutes(2));
        OssIssue graded = saveIssue(repo, 1, BODY, OPENED_AT.plusMinutes(1));
        given(gitHubClient.findIssue(repo.getGithubId(), 3)).willThrow(new GitHubClientException(
                GitHubClientException.Reason.REJECTED, 451, "GitHub 요청 실패(상태 451)", null));
        given(gitHubClient.findIssue(repo.getGithubId(), 2)).willThrow(new GitHubClientException(
                GitHubClientException.Reason.UNAVAILABLE, "GitHub가 응답하지 않습니다(시도 3번, 마지막 상태 502)", null));
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willReturn(found(1, BODY));
        given(issueGrader.grade(any(), any(), any())).willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(result).isEqualTo(new OssIssueGradingRunResult(StopReason.COMPLETED, 3, 1, 0, 0, 0, 0, 1,
                Map.of(OssIssueUngradableReason.BLOCKED, 1), Map.of()));
        assertThat(gradingFailures(rejected.getId())).isEqualTo(new GradingFailures(1, OssIssueBodyHash.of(BODY)));
        assertThat(gradingFailures(skippedByOutage.getId())).isEqualTo(new GradingFailures(0, null));
        assertThat(gradeRows(graded.getId())).hasSize(1);
        assertThat(ossIssueGradingService.gradeRepo(repo.getId()).selected()).isEqualTo(2);
    }

    @Test
    void gradeRepo_githubFailsTwiceInARow_stopsWithoutTouchingEitherIssue() {
        OssRepo repo = saveRepo();
        OssIssue skippedByOutage = saveIssue(repo, 2, BODY, OPENED_AT.plusMinutes(2));
        OssIssue heldByOutage = saveIssue(repo, 1, BODY, OPENED_AT.plusMinutes(1));
        GitHubClientException lastOutage = new GitHubClientException(GitHubClientException.Reason.INVALID_RESPONSE,
                200, "GitHub 이슈 응답을 읽지 못했습니다: /repositories/1296269/issues/1", null);
        given(gitHubClient.findIssue(repo.getGithubId(), 2)).willThrow(new GitHubClientException(
                GitHubClientException.Reason.UNAVAILABLE, "GitHub가 응답하지 않습니다(시도 3번, 마지막 상태 502)", null));
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willThrow(lastOutage);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(result).isEqualTo(new OssIssueGradingRunResult(StopReason.GITHUB_UNAVAILABLE, 2, 0, 1, 0, 0, 0, 1,
                Map.of(), Map.of()));
        assertThat(gradingFailures(skippedByOutage.getId())).isEqualTo(new GradingFailures(0, null));
        assertThat(gradingFailures(heldByOutage.getId())).isEqualTo(new GradingFailures(0, null));
        then(issueGrader).shouldHaveNoInteractions();
    }

    @Test
    void gradeRepo_graderUnavailable_storesNoGradeOrFailureAndSelectsTheIssueAgainNextTime() {
        OssRepo repo = saveRepo();
        OssIssue issue = saveIssue(repo, 1, BODY, OPENED_AT);
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willReturn(found(1, BODY));
        given(issueGrader.grade(any(), any(), any())).willThrow(
                new IssueGradingException(Reason.UNAVAILABLE, "이슈를 판정하지 못했습니다: Claude 꺼짐, GPT 꺼짐"));

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(result.stopReason()).isEqualTo(StopReason.GRADER_UNAVAILABLE);
        assertThat(result.held()).isEqualTo(1);
        assertThat(gradeRows(issue.getId())).isEmpty();
        assertThat(gradingFailures(issue.getId())).isEqualTo(new GradingFailures(0, null));
        assertThat(ossIssueGradingService.gradeRepo(repo.getId()).selected()).isEqualTo(1);
    }

    @Test
    void gradeRepo_issueDeletedWhileItWasBeingGraded_skipsOnlyThatIssue() {
        OssRepo repo = saveRepo();
        OssIssue deleted = saveIssue(repo, 2, BODY, OPENED_AT.plusMinutes(2));
        OssIssue kept = saveIssue(repo, 1, BODY, OPENED_AT.plusMinutes(1));
        given(gitHubClient.findIssue(eq(repo.getGithubId()), anyInt()))
                .willAnswer(invocation -> found(invocation.getArgument(1), BODY));
        given(issueGrader.grade(any(), any(), any())).willAnswer(invocation -> {
            jdbcTemplate.update("DELETE FROM oss_issue WHERE id = ?", deleted.getId());
            return GRADED;
        }).willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(result).isEqualTo(new OssIssueGradingRunResult(StopReason.COMPLETED, 2, 1, 0, 0, 0, 0, 0,
                Map.of(OssIssueUngradableReason.DELETED, 1), Map.of()));
        assertThat(gradeRows(kept.getId())).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oss_issue_grade", Long.class)).isEqualTo(1);
    }

    @Test
    void save_whileSyncUpdatesTheSameIssue_clearsFailuresFirstSoBothFinishWithoutDeadlock() throws Exception {
        OssRepo repo = saveRepo();
        OssIssue issue = saveIssue(repo, 1, BODY, OPENED_AT);
        jdbcTemplate.update(
                "UPDATE oss_issue SET grading_failures = 2, grading_failure_source_hash = ? WHERE id = ?",
                OssIssueBodyHash.of(BODY), issue.getId());
        OssIssue candidate = ossIssueRepository.findById(issue.getId()).orElseThrow();
        CountDownLatch cleared = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Answer<?> realRepository = mockingDetails(ossIssueRepository).getMockCreationSettings().getDefaultAnswer();
        willAnswer(invocation -> {
            Object updated = realRepository.answer(invocation);
            cleared.countDown();
            release.await(10, TimeUnit.SECONDS);
            return updated;
        }).given(ossIssueRepository).clearGradingFailures(issue.getId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> saving = executor.submit(
                    () -> ossIssueGradeSaver.save(candidate, VERDICT, MODEL, OssIssueBodyHash.of(BODY)));
            assertThat(cleared.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> syncing = executor.submit(() -> syncChangesTitle(issue.getId(), "Edited while grading"));

            assertThatThrownBy(() -> syncing.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            saving.get(10, TimeUnit.SECONDS);
            syncing.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(gradeRows(issue.getId())).hasSize(1);
        assertThat(gradingFailures(issue.getId())).isEqualTo(new GradingFailures(0, null));
        assertThat(storedIssue(issue.getId()).title()).isEqualTo("Edited while grading");
    }

    @Test
    void gradeRepo_gradesTheNewestTwentyFirstAndTheRestInTheNextRun() {
        OssRepo repo = saveRepo();
        IntStream.rangeClosed(1, 22).forEach(number ->
                saveIssue(repo, number, BODY + " " + number, OPENED_AT.plusMinutes(number)));
        given(gitHubClient.findIssue(eq(repo.getGithubId()), anyInt())).willAnswer(invocation -> {
            int number = invocation.getArgument(1);
            return found(number, BODY + " " + number);
        });
        given(issueGrader.grade(any(), any(), any())).willReturn(GRADED);

        OssIssueGradingRunResult first = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(first).isEqualTo(new OssIssueGradingRunResult(StopReason.ISSUE_LIMIT, 20, 20, 0, 0, 0, 0, 0,
                Map.of(), Map.of()));
        assertThat(ungradedNumbers(repo)).containsExactly(1, 2);

        OssIssueGradingRunResult second = ossIssueGradingService.gradeRepo(repo.getId());

        assertThat(second).isEqualTo(new OssIssueGradingRunResult(StopReason.COMPLETED, 2, 2, 0, 0, 0, 0, 0,
                Map.of(), Map.of()));
        assertThat(ungradedNumbers(repo)).isEmpty();
    }

    @Test
    void gradeRepo_sameRepoTwiceAtOnce_refusesTheSecondWhileHoldingTheLockForFifteenMinutesAtMost() throws Exception {
        OssRepo repo = saveRepo();
        saveIssue(repo, 1, BODY, OPENED_AT);
        CountDownLatch grading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willReturn(found(1, BODY));
        given(issueGrader.grade(any(), any(), any())).willAnswer(invocation -> {
            grading.countDown();
            release.await(10, TimeUnit.SECONDS);
            return GRADED;
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<OssIssueGradingRunResult> running =
                    executor.submit(() -> ossIssueGradingService.gradeRepo(repo.getId()));
            assertThat(grading.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> ossIssueGradingService.gradeRepo(repo.getId()))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OSS_ISSUE_GRADING_IN_PROGRESS));
            assertThat(gradingLock(repo.getId())).isEqualTo(new GradingLock(Duration.ofMinutes(15), true));

            release.countDown();
            assertThat(running.get(10, TimeUnit.SECONDS).graded()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(gradingLock(repo.getId()).held()).isFalse();
        then(gitHubClient).should(times(1)).findIssue(repo.getGithubId(), 1);
    }

    @Test
    void migrationV20_addsGradingFailureColumnsThatDefaultForRowsWrittenWithoutThem() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '20'", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForList("""
                SELECT CONCAT(column_name, ' ', column_type, ' ', is_nullable, ' ', COALESCE(column_default, 'none'))
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'oss_issue' AND column_name LIKE 'grading%'
                ORDER BY ordinal_position
                """, String.class))
                .containsExactly("grading_failures int NO 0", "grading_failure_source_hash varchar(64) YES none");

        OssRepo repo = saveRepo();
        jdbcTemplate.update("""
                INSERT INTO oss_issue (repo_id, github_issue_id, number, title, body_hash, github_created_at)
                VALUES (?, 99, 99, 'Written without grading columns', ?, ?)
                """, repo.getId(), OssIssueBodyHash.of(BODY), OPENED_AT);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM oss_issue WHERE github_issue_id = 99", Long.class);

        assertThat(gradingFailures(id)).isEqualTo(new GradingFailures(0, null));
    }

    private OssRepo saveRepo() {
        return ossRepoRepository.save(OssRepo.create(1_296_269L, "octocat/Hello-World", null, "Java", 1_000));
    }

    private OssIssue saveIssue(OssRepo repo, int number, String body, LocalDateTime openedAt) {
        return ossIssueRepository.save(OssIssue.create(repo, 1_000L + number, number, "Stored title " + number, body,
                openedAt));
    }

    private void syncChangesBody(Long issueId, String body) {
        transactionTemplate.executeWithoutResult(status -> {
            OssIssue issue = ossIssueRepository.findById(issueId).orElseThrow();
            issue.refresh(issue.getRepo(), issue.getNumber(), issue.getTitle(), body);
        });
    }

    private void syncChangesTitle(Long issueId, String title) {
        transactionTemplate.executeWithoutResult(status -> {
            OssIssue issue = ossIssueRepository.findById(issueId).orElseThrow();
            issue.refresh(issue.getRepo(), issue.getNumber(), title, BODY);
        });
    }

    private static GitHubIssueLookup found(int number, String body) {
        return GitHubIssueLookup.found(new GitHubIssueDetail(1_000L + number, "Fetched title " + number, body,
                List.of("bug"), true, false, 0, "octocat", "User"));
    }

    private List<GradeRow> gradeRows(Long issueId) {
        return jdbcTemplate.query("""
                SELECT criteria_version, model, source_hash, difficulty, reason_ko, summary_en
                FROM oss_issue_grade
                WHERE issue_id = ?
                ORDER BY id
                """, (row, rowNumber) -> new GradeRow(
                row.getString("criteria_version"),
                row.getString("model"),
                row.getString("source_hash"),
                row.getString("difficulty"),
                row.getString("reason_ko"),
                row.getString("summary_en")), issueId);
    }

    private GradingFailures gradingFailures(Long issueId) {
        return jdbcTemplate.queryForObject(
                "SELECT grading_failures, grading_failure_source_hash FROM oss_issue WHERE id = ?",
                (row, rowNumber) -> new GradingFailures(
                        row.getInt("grading_failures"), row.getString("grading_failure_source_hash")),
                issueId);
    }

    private StoredIssue storedIssue(Long issueId) {
        return jdbcTemplate.queryForObject("SELECT title, body_hash FROM oss_issue WHERE id = ?",
                (row, rowNumber) -> new StoredIssue(row.getString("title"), row.getString("body_hash")), issueId);
    }

    private List<Integer> ungradedNumbers(OssRepo repo) {
        return jdbcTemplate.queryForList("""
                SELECT i.number FROM oss_issue i
                WHERE i.repo_id = ?
                AND NOT EXISTS (SELECT 1 FROM oss_issue_grade g WHERE g.issue_id = i.id)
                ORDER BY i.number
                """, Integer.class, repo.getId());
    }

    private GradingLock gradingLock(Long repoId) {
        return jdbcTemplate.queryForObject("""
                SELECT TIMESTAMPDIFF(SECOND, locked_at, lock_until) AS lock_seconds,
                       lock_until > UTC_TIMESTAMP(3) AS held
                FROM shedlock
                WHERE name = ?
                """, (row, rowNumber) -> new GradingLock(
                Duration.ofSeconds(row.getLong("lock_seconds")),
                row.getBoolean("held")), GRADING_LOCK_PREFIX + repoId);
    }

    private record GradeRow(String criteriaVersion, String model, String sourceHash, String difficulty,
                            String reasonKo, String summaryEn) {
    }

    private record GradingFailures(int count, String sourceHash) {
    }

    private record StoredIssue(String title, String bodyHash) {
    }

    private record GradingLock(Duration lockAtMostFor, boolean held) {
    }
}
