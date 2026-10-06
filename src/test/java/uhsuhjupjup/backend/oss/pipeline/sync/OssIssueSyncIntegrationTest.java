package uhsuhjupjup.backend.oss.pipeline.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssue;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueListResult;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.pipeline.sync.application.OssIssueSyncSaver;
import uhsuhjupjup.backend.oss.pipeline.sync.application.OssIssueSyncService;
import uhsuhjupjup.backend.oss.pipeline.sync.application.dto.OssIssueSyncResult;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter.ExclusionReason;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssRepoSyncState;
import uhsuhjupjup.backend.oss.pipeline.sync.infra.OssRepoSyncStateRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class OssIssueSyncIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 19, 30, 0);
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 30, 10, 0, 0);
    private static final LocalDateTime LONG_AGO = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String OWNER = "octocat";
    private static final String NAME = "Hello-World";
    private static final String OTHER_NAME = "Spoon-Knife";
    private static final String ETAG = "W/\"a1b2c3\"";
    private static final String NEW_ETAG = "W/\"d4e5f6\"";

    @Autowired
    private OssIssueSyncService ossIssueSyncService;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private OssRepoSyncStateRepository ossRepoSyncStateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockitoBean
    private GitHubClient gitHubClient;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private OssIssueSyncSaver ossIssueSyncSaver;

    @MockitoSpyBean
    private OssIssueRepository ossIssueRepository;

    private long nextRepoGithubId = 1;

    @BeforeEach
    void setUp() {
        ossRepoRepository.deleteAllInBatch();
        given(clock.instant()).willReturn(NOW.atZone(KST).toInstant());
        given(clock.getZone()).willReturn(KST);
    }

    @Test
    void syncRepo_firstTime_readsLastSevenDaysOutsideTransactionAndStoresOnlyIssuesPassingPrefilter() {
        Long repoId = saveRepo(NAME);
        AtomicBoolean readInsideTransaction = new AtomicBoolean(true);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willAnswer(invocation -> {
            readInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return changed(
                    issue(101, 1347, "Found a bug", "Steps to reproduce", today(10, 0)),
                    pullRequest(102, today(12, 0)),
                    botIssue(103, today(11, 0)),
                    assignedIssue(104, "Taken already", "Being fixed", today(11, 30)));
        });

        OssIssueSyncResult result = ossIssueSyncService.syncRepo(repoId);

        assertThat(readInsideTransaction).isFalse();
        then(gitHubClient).should().listOpenIssues(OWNER, NAME, null, LocalDateTime.of(2026, 9, 26, 19, 30, 0));
        assertThat(result).isEqualTo(new OssIssueSyncResult(false, null, 4,
                Map.of(ExclusionReason.PULL_REQUEST, 1, ExclusionReason.BOT_AUTHOR, 1, ExclusionReason.ASSIGNED, 1),
                1, 0, 0));
        assertThat(storedGithubIssueIds()).containsExactly(101L);
        assertThat(storedIssue(101))
                .isEqualTo(new StoredIssue(repoId, 1347, "Found a bug", sha256Hex("Steps to reproduce"), OPENED_AT));
        assertThat(storedState(repoId)).isEqualTo(new StoredState(NEW_ETAG, today(12, 0), NOW, 0));
    }

    @Test
    void syncRepo_sameIssuesAgain_storesEachOnceAndLeavesRowsUntouched() {
        Long repoId = saveRepo(NAME);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(changed(
                issue(201, 1, "First", "body one", today(10, 0)),
                issue(202, 2, "Second", "body two", today(11, 0))));
        ossIssueSyncService.syncRepo(repoId);
        Map<Long, Long> rowIds = rowIdsByGithubIssueId();
        markIssuesUpdatedLongAgo();

        OssIssueSyncResult again = ossIssueSyncService.syncRepo(repoId);

        assertThat(again).isEqualTo(new OssIssueSyncResult(false, null, 2, Map.of(), 0, 0, 2));
        assertThat(rowIdsByGithubIssueId()).hasSize(2).isEqualTo(rowIds);
        assertThat(updatedAtOf(201)).isEqualTo(LONG_AGO);
        assertThat(updatedAtOf(202)).isEqualTo(LONG_AGO);
        then(gitHubClient).should().listOpenIssues(OWNER, NAME, NEW_ETAG, today(10, 55));
    }

    @Test
    void syncRepo_bodyEdited_updatesHashButLineBreakAndTrailingSpaceEditsKeepIt() {
        Long repoId = saveRepo(NAME);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(
                changed(issue(301, 5, "Crash on start", "Steps\n1. run it", today(10, 0))),
                changed(issue(301, 5, "Crash on start", "Steps  \r\n1. run it\t\r\n\r\n", today(10, 10))),
                changed(issue(301, 5, "Crash on start", "Steps\n1. run it twice", today(10, 20))));
        ossIssueSyncService.syncRepo(repoId);
        markIssuesUpdatedLongAgo();

        OssIssueSyncResult whitespaceOnly = ossIssueSyncService.syncRepo(repoId);

        assertThat(whitespaceOnly.bodyChanged()).isZero();
        assertThat(whitespaceOnly.bodyUnchanged()).isEqualTo(1);
        assertThat(storedIssue(301).bodyHash()).isEqualTo(sha256Hex("Steps\n1. run it"));
        assertThat(updatedAtOf(301)).isEqualTo(LONG_AGO);

        OssIssueSyncResult edited = ossIssueSyncService.syncRepo(repoId);

        assertThat(edited.bodyChanged()).isEqualTo(1);
        assertThat(edited.bodyUnchanged()).isZero();
        assertThat(storedIssue(301).bodyHash()).isEqualTo(sha256Hex("Steps\n1. run it twice"));
        assertThat(updatedAtOf(301)).isAfter(LONG_AGO);
        assertThat(storedGithubIssueIds()).containsExactly(301L);
    }

    @Test
    void syncRepo_titleEditedWithSameBody_storesNewTitleCutTo256CodePoints() {
        Long repoId = saveRepo(NAME);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(
                changed(issue(351, 6, "Short title", "same body", today(10, 0))),
                changed(issue(351, 6, "🐛".repeat(300), "same body", today(10, 5))));
        ossIssueSyncService.syncRepo(repoId);

        OssIssueSyncResult result = ossIssueSyncService.syncRepo(repoId);

        assertThat(result.bodyUnchanged()).isEqualTo(1);
        assertThat(storedIssue(351).title()).isEqualTo("🐛".repeat(256));
    }

    @Test
    void syncRepo_storedIssueNowAssigned_isLeftAsItWasWhileLatestUpdateStillMoves() {
        Long repoId = saveRepo(NAME);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(
                changed(issue(401, 9, "Flaky test", "body v1", today(10, 0))),
                changed(assignedIssue(401, "Flaky test (taken)", "body v2", today(10, 30))));
        ossIssueSyncService.syncRepo(repoId);
        markIssuesUpdatedLongAgo();

        OssIssueSyncResult result = ossIssueSyncService.syncRepo(repoId);

        assertThat(result)
                .isEqualTo(new OssIssueSyncResult(false, null, 1, Map.of(ExclusionReason.ASSIGNED, 1), 0, 0, 0));
        assertThat(storedIssue(401))
                .isEqualTo(new StoredIssue(repoId, 9, "Flaky test", sha256Hex("body v1"), OPENED_AT));
        assertThat(updatedAtOf(401)).isEqualTo(LONG_AGO);
        assertThat(storedState(repoId).lastIssueUpdatedAt()).isEqualTo(today(10, 30));
    }

    @Test
    void syncRepo_issueTransferredFromAnotherRepo_movesStoredRowToThisRepoAndNumber() {
        Long helloWorld = saveRepo(NAME);
        Long spoonKnife = saveRepo(OTHER_NAME);
        given(gitHubClient.listOpenIssues(eq(OWNER), eq(NAME), any(), any()))
                .willReturn(changed(issue(501, 12, "Move me", "body", today(10, 0))));
        given(gitHubClient.listOpenIssues(eq(OWNER), eq(OTHER_NAME), any(), any()))
                .willReturn(changed(issue(501, 3, "Move me", "body", today(10, 5))));
        ossIssueSyncService.syncRepo(helloWorld);
        Map<Long, Long> rowIds = rowIdsByGithubIssueId();

        OssIssueSyncResult result = ossIssueSyncService.syncRepo(spoonKnife);

        assertThat(result).isEqualTo(new OssIssueSyncResult(false, null, 1, Map.of(), 0, 0, 1));
        assertThat(rowIdsByGithubIssueId()).isEqualTo(rowIds);
        assertThat(storedIssue(501)).isEqualTo(new StoredIssue(spoonKnife, 3, "Move me", sha256Hex("body"), OPENED_AT));
    }

    @Test
    void syncRepo_notModified_keepsEtagAndLatestUpdateAndRecordsOnlySyncTime() {
        Long repoId = saveRepo(NAME);
        givenState(repoId, ETAG, today(18, 0), LONG_AGO, 2);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any()))
                .willReturn(GitHubIssueListResult.unchanged(ETAG));

        OssIssueSyncResult result = ossIssueSyncService.syncRepo(repoId);

        assertThat(result).isEqualTo(new OssIssueSyncResult(true, null, 0, Map.of(), 0, 0, 0));
        then(gitHubClient).should().listOpenIssues(OWNER, NAME, ETAG, today(17, 55));
        assertThat(storedState(repoId)).isEqualTo(new StoredState(ETAG, today(18, 0), NOW, 0));
    }

    @Test
    void syncRepo_onlyIssuesOlderThanLatestUpdate_keepsLatestUpdateButTakesNewEtag() {
        Long repoId = saveRepo(NAME);
        givenState(repoId, ETAG, today(18, 0), LONG_AGO, 0);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any()))
                .willReturn(changed(issue(551, 1, "Edited a bit earlier", "body", today(17, 57))));

        ossIssueSyncService.syncRepo(repoId);

        assertThat(storedState(repoId)).isEqualTo(new StoredState(NEW_ETAG, today(18, 0), NOW, 0));
        assertThat(storedGithubIssueIds()).containsExactly(551L);
    }

    @Test
    void syncRepo_readSeveralPagesToTheEndWithoutEtag_nextReadStartsAtLatestUpdateWithoutOverlap() {
        Long repoId = saveRepo(NAME);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(
                GitHubIssueListResult.changed(null, List.of(
                        issue(651, 1, "One", "body one", today(10, 0)),
                        issue(652, 2, "Two", "body two", today(11, 0)))),
                changed());

        OssIssueSyncResult result = ossIssueSyncService.syncRepo(repoId);

        assertThat(result).isEqualTo(new OssIssueSyncResult(false, null, 2, Map.of(), 2, 0, 0));
        assertThat(storedState(repoId)).isEqualTo(new StoredState(null, today(11, 0), NOW, 0));

        ossIssueSyncService.syncRepo(repoId);

        then(gitHubClient).should().listOpenIssues(OWNER, NAME, null, today(11, 0));
    }

    @Test
    void syncRepo_readCutAtPageLimit_storesWhatWasReadWithoutEtagOrFailureAndNextReadResumesWithoutOverlap() {
        Long repoId = saveRepo(NAME);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(
                GitHubIssueListResult.partial(List.of(
                        issue(601, 1, "One", "body one", today(10, 0)),
                        issue(602, 2, "Two", "body two", today(11, 0))),
                        GitHubIssueListResult.IncompleteReason.PAGE_LIMIT),
                changed());

        OssIssueSyncResult result = ossIssueSyncService.syncRepo(repoId);

        assertThat(result).isEqualTo(new OssIssueSyncResult(
                false, OssIssueSyncResult.IncompleteReason.PAGE_LIMIT, 2, Map.of(), 2, 0, 0));
        assertThat(storedGithubIssueIds()).containsExactly(601L, 602L);
        assertThat(storedState(repoId)).isEqualTo(new StoredState(null, today(11, 0), NOW, 0));

        ossIssueSyncService.syncRepo(repoId);

        then(gitHubClient).should().listOpenIssues(OWNER, NAME, null, today(11, 0));
    }

    @Test
    void syncRepo_readCutByUnavailablePage_storesWhatWasReadAndCountsFailure() {
        Long repoId = saveRepo(NAME);
        givenState(repoId, ETAG, today(9, 0), LONG_AGO, 1);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(GitHubIssueListResult.partial(
                List.of(issue(701, 1, "One", "body one", today(10, 0))),
                GitHubIssueListResult.IncompleteReason.PAGE_UNAVAILABLE));

        OssIssueSyncResult result = ossIssueSyncService.syncRepo(repoId);

        assertThat(result).isEqualTo(new OssIssueSyncResult(
                false, OssIssueSyncResult.IncompleteReason.PAGE_UNAVAILABLE, 1, Map.of(), 1, 0, 0));
        assertThat(storedGithubIssueIds()).containsExactly(701L);
        assertThat(storedState(repoId)).isEqualTo(new StoredState(null, today(10, 0), NOW, 2));
    }

    @Test
    void syncRepo_firstReadRejectedByGitHub_leavesStateWithOneFailureAndRethrows() {
        Long repoId = saveRepo(NAME);
        GitHubClientException notFound = new GitHubClientException(
                Reason.REJECTED, 404, "GitHub 요청 실패(상태 404): /repos/octocat/Hello-World/issues", null);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willThrow(notFound);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(repoId)).isSameAs(notFound);

        assertThat(storedState(repoId)).isEqualTo(new StoredState(null, null, null, 1));
        assertThat(storedGithubIssueIds()).isEmpty();
    }

    @Test
    void syncRepo_githubKeepsFailingForRepo_countsUpKeepingEtagLatestUpdateAndSyncTime() {
        Long repoId = saveRepo(NAME);
        givenState(repoId, ETAG, today(18, 0), LONG_AGO, 2);
        GitHubClientException unavailable = new GitHubClientException(
                Reason.UNAVAILABLE, "GitHub가 응답하지 않습니다(시도 3번, 마지막 상태 502)", null);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willThrow(unavailable);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(repoId)).isSameAs(unavailable);

        assertThat(storedState(repoId)).isEqualTo(new StoredState(ETAG, today(18, 0), LONG_AGO, 3));
    }

    @Test
    void syncRepo_rateLimited_rethrowsWithoutCountingFailure() {
        Long repoId = saveRepo(NAME);
        givenState(repoId, ETAG, today(18, 0), LONG_AGO, 0);
        GitHubClientException rateLimited = new GitHubClientException(
                Reason.RATE_LIMITED, 403, "GitHub 요청 실패(상태 403): /repos/octocat/Hello-World/issues", null);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willThrow(rateLimited);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(repoId)).isSameAs(rateLimited);

        assertThat(storedState(repoId)).isEqualTo(new StoredState(ETAG, today(18, 0), LONG_AGO, 0));
    }

    @Test
    void syncRepo_suspendedOrMissingRepo_throwsNotFoundWithoutCallingGitHubOrCreatingState() {
        Long suspended = saveRepo(NAME);
        jdbcTemplate.update("UPDATE oss_repo SET status = 'SUSPENDED' WHERE id = ?", suspended);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(suspended))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OSS_REPO_NOT_FOUND));
        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(suspended + 1000))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OSS_REPO_NOT_FOUND));

        then(gitHubClient).shouldHaveNoInteractions();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oss_repo_sync_state", Long.class)).isZero();
    }

    @Test
    void syncRepo_sameRepoTwiceAtOnceForTheFirstTime_bothFinishWithOneStateAndEachIssueOnce() throws Exception {
        Long repoId = saveRepo(NAME);
        CyclicBarrier bothFoundNoState = new CyclicBarrier(2);
        willAnswer(invocation -> {
            bothFoundNoState.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).given(ossIssueSyncSaver).register(repoId);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willAnswer(invocation -> {
            bothRead.await(10, TimeUnit.SECONDS);
            return changed(
                    issue(801, 1, "One", "body one", today(10, 0)),
                    issue(802, 2, "Two", "body two", today(11, 0)));
        });

        List<OssIssueSyncResult> results = syncTogether(repoId, repoId);

        then(ossIssueSyncSaver).should(times(2)).register(repoId);
        then(ossIssueSyncSaver).should(times(2)).save(eq(repoId), any(), any());
        assertThat(results).extracting(OssIssueSyncResult::created).containsExactlyInAnyOrder(2, 0);
        assertThat(results).extracting(OssIssueSyncResult::bodyUnchanged).containsExactlyInAnyOrder(0, 2);
        assertThat(storedGithubIssueIds()).containsExactly(801L, 802L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oss_repo_sync_state WHERE repo_id = ?", Long.class, repoId)).isEqualTo(1);
        assertThat(storedState(repoId)).isEqualTo(new StoredState(NEW_ETAG, today(11, 0), NOW, 0));
    }

    @Test
    void syncRepo_whileAnotherTransactionHoldsState_waitsAndAppliesOnTopOfWhatItCommitted() throws Exception {
        Long repoId = saveRepo(NAME);
        givenState(repoId, ETAG, today(9, 0), LONG_AGO, 0);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any()))
                .willReturn(changed(issue(901, 1, "One", "body one", today(10, 0))));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                OssRepoSyncState state = ossRepoSyncStateRepository.findForUpdateByRepoId(repoId).orElseThrow();
                state.recordRead(today(12, 0), "W/\"other-sync\"", NOW.minusMinutes(1));
                locked.countDown();
                awaitRelease(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<OssIssueSyncResult> sync = executor.submit(() -> ossIssueSyncService.syncRepo(repoId));

            assertThatThrownBy(() -> sync.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertThat(sync.get(10, TimeUnit.SECONDS).created()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        then(gitHubClient).should().listOpenIssues(OWNER, NAME, ETAG, today(8, 55));
        assertThat(storedState(repoId)).isEqualTo(new StoredState(NEW_ETAG, today(12, 0), NOW, 0));
        assertThat(storedGithubIssueIds()).containsExactly(901L);
    }

    @Test
    void syncRepo_twoReposListSameNewIssuesInOppositeOrderAtOnce_loserSavesOnceMoreAfterUniqueKeyClashNotDeadlock()
            throws Exception {
        Long helloWorld = saveRepo(NAME);
        Long spoonKnife = saveRepo(OTHER_NAME);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willAnswer(invocation ->
                NAME.equals(invocation.getArgument(1))
                        ? changed(issue(1002, 12, "Second", "body two", today(10, 0)),
                                issue(1001, 11, "First", "body one", today(10, 0)))
                        : changed(issue(1001, 3, "First", "body one", today(10, 0)),
                                issue(1002, 4, "Second", "body two", today(10, 0))));
        Answer<?> realRepository = mockingDetails(ossIssueRepository).getMockCreationSettings().getDefaultAnswer();
        AtomicInteger lookups = new AtomicInteger();
        CyclicBarrier bothLookedUpBeforeInserting = new CyclicBarrier(2);
        willAnswer(invocation -> {
            Object found = realRepository.answer(invocation);
            if (lookups.incrementAndGet() <= 2) {
                bothLookedUpBeforeInserting.await(10, TimeUnit.SECONDS);
            }
            return found;
        }).given(ossIssueRepository).findAllByGithubIssueIdIn(any());
        List<Throwable> saveFailures = new CopyOnWriteArrayList<>();
        willAnswer(invocation -> {
            try {
                return invocation.callRealMethod();
            } catch (RuntimeException e) {
                saveFailures.add(e);
                throw e;
            }
        }).given(ossIssueSyncSaver).save(any(), any(), any());

        List<OssIssueSyncResult> results = syncTogether(helloWorld, spoonKnife);

        assertThat(results).hasSize(2);
        then(ossIssueSyncSaver).should(times(3)).save(any(), any(), any());
        assertThat(saveFailures).singleElement().isInstanceOf(DataIntegrityViolationException.class);
        assertThat(storedGithubIssueIds()).containsExactly(1001L, 1002L);
        StoredIssue first = storedIssue(1001);
        StoredIssue second = storedIssue(1002);
        assertThat(second.repoId()).isEqualTo(first.repoId());
        assertThat(first.repoId()).isIn(helloWorld, spoonKnife);
        assertThat(List.of(first.number(), second.number()))
                .isEqualTo(first.repoId() == helloWorld ? List.of(11, 12) : List.of(3, 4));
    }

    private Long saveRepo(String name) {
        return ossRepoRepository.save(OssRepo.create(nextRepoGithubId++, OWNER + "/" + name, null, "Java", 1_000))
                .getId();
    }

    private void givenState(Long repoId, String etag, LocalDateTime lastIssueUpdatedAt, LocalDateTime lastSyncedAt,
                            int consecutiveFailures) {
        jdbcTemplate.update("""
                INSERT INTO oss_repo_sync_state
                    (repo_id, etag, last_issue_updated_at, last_synced_at, consecutive_failures)
                VALUES (?, ?, ?, ?, ?)
                """, repoId, etag, lastIssueUpdatedAt, lastSyncedAt, consecutiveFailures);
    }

    private void markIssuesUpdatedLongAgo() {
        jdbcTemplate.update("UPDATE oss_issue SET updated_at = ?", LONG_AGO);
    }

    private List<Long> storedGithubIssueIds() {
        return jdbcTemplate.queryForList("SELECT github_issue_id FROM oss_issue ORDER BY github_issue_id", Long.class);
    }

    private Map<Long, Long> rowIdsByGithubIssueId() {
        Map<Long, Long> rowIds = new HashMap<>();
        jdbcTemplate.query("SELECT id, github_issue_id FROM oss_issue",
                row -> {
                    rowIds.put(row.getLong("github_issue_id"), row.getLong("id"));
                });
        return rowIds;
    }

    private StoredIssue storedIssue(long githubIssueId) {
        return jdbcTemplate.queryForObject("""
                SELECT repo_id, number, title, body_hash, github_created_at
                FROM oss_issue
                WHERE github_issue_id = ?
                """, (row, rowNumber) -> new StoredIssue(
                row.getLong("repo_id"),
                row.getInt("number"),
                row.getString("title"),
                row.getString("body_hash"),
                row.getObject("github_created_at", LocalDateTime.class)), githubIssueId);
    }

    private LocalDateTime updatedAtOf(long githubIssueId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM oss_issue WHERE github_issue_id = ?", LocalDateTime.class, githubIssueId);
    }

    private StoredState storedState(Long repoId) {
        return jdbcTemplate.queryForObject("""
                SELECT etag, last_issue_updated_at, last_synced_at, consecutive_failures
                FROM oss_repo_sync_state
                WHERE repo_id = ?
                """, (row, rowNumber) -> new StoredState(
                row.getString("etag"),
                row.getObject("last_issue_updated_at", LocalDateTime.class),
                row.getObject("last_synced_at", LocalDateTime.class),
                row.getInt("consecutive_failures")), repoId);
    }

    private List<OssIssueSyncResult> syncTogether(Long... repoIds) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(repoIds.length);
        try {
            List<Callable<OssIssueSyncResult>> syncs = new ArrayList<>();
            for (Long repoId : repoIds) {
                syncs.add(() -> ossIssueSyncService.syncRepo(repoId));
            }
            List<OssIssueSyncResult> results = new ArrayList<>();
            for (Future<OssIssueSyncResult> sync : executor.invokeAll(syncs, 30, TimeUnit.SECONDS)) {
                results.add(sync.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static LocalDateTime today(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 3, hour, minute, 0);
    }

    private static GitHubIssueListResult changed(GitHubIssue... issues) {
        return GitHubIssueListResult.changed(NEW_ETAG, List.of(issues));
    }

    private static GitHubIssue issue(long githubId, int number, String title, String body, LocalDateTime updatedAt) {
        return new GitHubIssue(githubId, number, title, body, false, 0, "octocat", "User", OPENED_AT, updatedAt);
    }

    private static GitHubIssue assignedIssue(long githubId, String title, String body, LocalDateTime updatedAt) {
        return new GitHubIssue(githubId, 9, title, body, false, 1, "octocat", "User", OPENED_AT, updatedAt);
    }

    private static GitHubIssue pullRequest(long githubId, LocalDateTime updatedAt) {
        return new GitHubIssue(githubId, 20, "Bump spring-boot to 3.5.15", "Release notes", true, 0,
                "octocat", "User", OPENED_AT, updatedAt);
    }

    private static GitHubIssue botIssue(long githubId, LocalDateTime updatedAt) {
        return new GitHubIssue(githubId, 21, "Dependency Dashboard", "Renovate dashboard", false, 0,
                "renovate[bot]", "Bot", OPENED_AT, updatedAt);
    }

    private static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record StoredIssue(long repoId, int number, String title, String bodyHash,
                               LocalDateTime githubCreatedAt) {
    }

    private record StoredState(String etag, LocalDateTime lastIssueUpdatedAt, LocalDateTime lastSyncedAt,
                               int consecutiveFailures) {
    }
}
