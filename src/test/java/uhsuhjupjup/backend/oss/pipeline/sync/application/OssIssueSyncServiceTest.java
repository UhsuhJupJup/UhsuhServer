package uhsuhjupjup.backend.oss.pipeline.sync.application;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentialsMissingException;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssue;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueListResult;
import uhsuhjupjup.backend.oss.pipeline.sync.application.dto.OssIssueSyncResult;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssRepoSyncState;
import uhsuhjupjup.backend.oss.pipeline.sync.infra.OssRepoSyncStateRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OssIssueSyncServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 19, 30, 0);
    private static final LocalDateTime LATEST_UPDATE = LocalDateTime.of(2026, 10, 3, 18, 42, 7);
    private static final Long REPO_ID = 7L;
    private static final String OWNER = "octocat";
    private static final String NAME = "Hello-World";
    private static final String ETAG = "W/\"a1b2c3\"";
    private static final String NEW_ETAG = "W/\"d4e5f6\"";
    private static final GitHubIssueListResult LISTED = GitHubIssueListResult.changed(NEW_ETAG, List.of(
            new GitHubIssue(5612345678L, 1347, "Found a bug", "Steps to reproduce", false, 0, "octocat", "User",
                    LocalDateTime.of(2026, 9, 28, 17, 11, 25), LocalDateTime.of(2026, 10, 3, 19, 10, 0))));
    private static final OssIssueSyncResult SAVED = new OssIssueSyncResult(false, null, 1, Map.of(), 1, 0, 0);

    @Mock
    private GitHubClient gitHubClient;

    @Mock
    private OssRepoRepository ossRepoRepository;

    @Mock
    private OssRepoSyncStateRepository ossRepoSyncStateRepository;

    @Mock
    private OssIssueSyncSaver ossIssueSyncSaver;

    @Mock
    private LockProvider lockProvider;

    @Mock
    private SimpleLock syncLock;

    private final OssRepo repo = OssRepo.create(1296269L, OWNER + "/" + NAME, null, "Java", 80);

    private OssIssueSyncService ossIssueSyncService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(repo, "id", REPO_ID);
        lenient().when(lockProvider.lock(any())).thenReturn(Optional.of(syncLock));
        ossIssueSyncService = new OssIssueSyncService(gitHubClient, ossRepoRepository, ossRepoSyncStateRepository,
                ossIssueSyncSaver, lockProvider, Clock.fixed(NOW.atZone(KST).toInstant(), KST));
    }

    @Test
    void syncRepo_missingOrSuspendedRepo_throwsNotFoundWithoutTouchingGitHubOrState() {
        given(ossRepoRepository.findByIdAndStatus(REPO_ID, OssRepoStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OSS_REPO_NOT_FOUND));

        then(lockProvider).shouldHaveNoInteractions();
        then(gitHubClient).shouldHaveNoInteractions();
        then(ossRepoSyncStateRepository).shouldHaveNoInteractions();
        then(ossIssueSyncSaver).shouldHaveNoInteractions();
    }

    @Test
    void syncRepo_holdsRepoLockFromReadingStateUntilSavedThenReleasesIt() {
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW)).willReturn(SAVED);

        assertThat(ossIssueSyncService.syncRepo(REPO_ID)).isEqualTo(SAVED);

        ArgumentCaptor<LockConfiguration> lockConfiguration = ArgumentCaptor.forClass(LockConfiguration.class);
        InOrder inOrder = inOrder(lockProvider, ossRepoSyncStateRepository, gitHubClient, ossIssueSyncSaver, syncLock);
        inOrder.verify(lockProvider).lock(lockConfiguration.capture());
        inOrder.verify(ossRepoSyncStateRepository).findByRepoId(REPO_ID);
        inOrder.verify(gitHubClient).listOpenIssues(any(), any(), any(), any());
        inOrder.verify(ossIssueSyncSaver).save(REPO_ID, LISTED, NOW);
        inOrder.verify(syncLock).unlock();
        assertThat(lockConfiguration.getValue().getName()).isEqualTo("ossIssueSync-7");
        assertThat(lockConfiguration.getValue().getLockAtMostFor()).isEqualTo(Duration.ofMinutes(20));
        assertThat(lockConfiguration.getValue().getLockAtLeastFor()).isZero();
    }

    @Test
    void syncRepo_anotherSyncHoldsRepoLock_throwsInProgressWithoutReadingStateOrGitHub() {
        givenActiveRepo();
        given(lockProvider.lock(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OSS_ISSUE_SYNC_IN_PROGRESS));

        then(ossRepoSyncStateRepository).shouldHaveNoInteractions();
        then(gitHubClient).shouldHaveNoInteractions();
        then(ossIssueSyncSaver).shouldHaveNoInteractions();
        then(syncLock).shouldHaveNoInteractions();
    }

    @Test
    void syncRepo_githubFails_releasesRepoLockAndRethrows() {
        GitHubClientException unavailable = new GitHubClientException(
                Reason.UNAVAILABLE, "GitHub가 응답하지 않습니다(시도 3번)", null);
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willThrow(unavailable);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID)).isSameAs(unavailable);

        then(syncLock).should().unlock();
    }

    @Test
    void syncRepo_savingFails_releasesRepoLockAndRethrows() {
        BusinessException repoGone = new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND);
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW)).willThrow(repoGone);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID)).isSameAs(repoGone);

        then(syncLock).should().unlock();
    }

    @Test
    void syncRepo_releasingLockFails_stillReturnsResult() {
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW)).willReturn(SAVED);
        willThrow(new DataAccessResourceFailureException("Communications link failure")).given(syncLock).unlock();

        assertThat(ossIssueSyncService.syncRepo(REPO_ID)).isEqualTo(SAVED);
    }

    @Test
    void syncRepo_githubFailsAndReleasingLockFails_throwsGitHubFailure() {
        GitHubClientException rateLimited = new GitHubClientException(
                Reason.RATE_LIMITED, 403, "GitHub 요청 실패(상태 403)", null);
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willThrow(rateLimited);
        willThrow(new DataAccessResourceFailureException("Communications link failure")).given(syncLock).unlock();

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID)).isSameAs(rateLimited);
    }

    @Test
    void syncRepo_firstTime_registersStateThenReadsLastSevenDaysWithoutEtagThenSavesWithNow() {
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.empty());
        given(ossIssueSyncSaver.register(REPO_ID)).willReturn(newState());
        given(gitHubClient.listOpenIssues(OWNER, NAME, null, LocalDateTime.of(2026, 9, 26, 19, 30, 0)))
                .willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW)).willReturn(SAVED);

        assertThat(ossIssueSyncService.syncRepo(REPO_ID)).isEqualTo(SAVED);

        InOrder inOrder = inOrder(ossIssueSyncSaver, gitHubClient);
        inOrder.verify(ossIssueSyncSaver).register(REPO_ID);
        inOrder.verify(gitHubClient).listOpenIssues(OWNER, NAME, null, LocalDateTime.of(2026, 9, 26, 19, 30, 0));
        inOrder.verify(ossIssueSyncSaver).save(REPO_ID, LISTED, NOW);
        then(ossIssueSyncSaver).should(never()).recordFailure(any());
    }

    @Test
    void syncRepo_anotherSyncRegisteredStateFirst_continuesWithThatState() {
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(stateReadKeepingEtag()));
        given(ossIssueSyncSaver.register(REPO_ID))
                .willThrow(new DataIntegrityViolationException("uk_oss_repo_sync_state_repo_id"));
        given(gitHubClient.listOpenIssues(OWNER, NAME, ETAG, LATEST_UPDATE.minusMinutes(5))).willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW)).willReturn(SAVED);

        assertThat(ossIssueSyncService.syncRepo(REPO_ID)).isEqualTo(SAVED);

        then(ossRepoSyncStateRepository).should(times(2)).findByRepoId(REPO_ID);
    }

    @Test
    void syncRepo_registeringFailsAndNoStateAppears_throwsNotFoundWithoutCallingGitHub() {
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.empty());
        given(ossIssueSyncSaver.register(REPO_ID))
                .willThrow(new DataIntegrityViolationException("fk_oss_repo_sync_state_repo"));

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OSS_REPO_NOT_FOUND));

        then(gitHubClient).shouldHaveNoInteractions();
    }

    @Test
    void syncRepo_afterReadKeepingEtag_readsFromFiveMinutesBeforeLatestUpdateWithThatEtag() {
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(OWNER, NAME, ETAG, LocalDateTime.of(2026, 10, 3, 18, 37, 7)))
                .willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW)).willReturn(SAVED);

        assertThat(ossIssueSyncService.syncRepo(REPO_ID)).isEqualTo(SAVED);

        then(ossIssueSyncSaver).should(never()).register(any());
    }

    @Test
    void syncRepo_afterSeveralPagesReadWithoutEtag_resumesAtLatestUpdateWithoutEtag() {
        OssRepoSyncState readSeveralPages = stateReadKeepingEtag();
        readSeveralPages.recordRead(LATEST_UPDATE.plusMinutes(1), null, NOW.minusMinutes(30));
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(readSeveralPages));
        given(gitHubClient.listOpenIssues(OWNER, NAME, null, LATEST_UPDATE.plusMinutes(1))).willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW)).willReturn(SAVED);

        assertThat(ossIssueSyncService.syncRepo(REPO_ID)).isEqualTo(SAVED);
    }

    @Test
    void syncRepo_afterReadCutShort_resumesAtLatestUpdateWithoutEtag() {
        OssRepoSyncState cutShort = stateReadKeepingEtag();
        cutShort.recordReadCutByFailure(LATEST_UPDATE.plusMinutes(1), NOW.minusMinutes(30));
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(cutShort));
        given(gitHubClient.listOpenIssues(OWNER, NAME, null, LATEST_UPDATE.plusMinutes(1))).willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW)).willReturn(SAVED);

        assertThat(ossIssueSyncService.syncRepo(REPO_ID)).isEqualTo(SAVED);
    }

    static Stream<GitHubClientException> failuresBeyondRepo() {
        return Stream.of(
                new GitHubCredentialsMissingException(),
                new GitHubClientException(Reason.UNAUTHORIZED, 401, "GitHub 요청 실패(상태 401)", null),
                new GitHubClientException(Reason.RATE_LIMITED, 403, "GitHub 요청 실패(상태 403)", null),
                new GitHubClientException(Reason.RATE_LIMITED, 429, "GitHub 요청 실패(상태 429)", null));
    }

    @ParameterizedTest
    @MethodSource("failuresBeyondRepo")
    void syncRepo_githubFailureBeyondRepo_rethrowsItWithoutCountingRepoFailure(GitHubClientException failure) {
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willThrow(failure);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID)).isSameAs(failure);

        then(ossIssueSyncSaver).shouldHaveNoInteractions();
    }

    static Stream<GitHubClientException> repoFailures() {
        return Stream.of(
                new GitHubClientException(Reason.REJECTED, 403, "GitHub 요청 실패(상태 403)", null),
                new GitHubClientException(Reason.REJECTED, 404, "GitHub 요청 실패(상태 404)", null),
                new GitHubClientException(Reason.REJECTED, 410, "GitHub 요청 실패(상태 410)", null),
                new GitHubClientException(Reason.INVALID_RESPONSE, 200, "GitHub 이슈 목록 응답을 읽지 못했습니다", null),
                new GitHubClientException(Reason.REDIRECT_REFUSED, 302, "GitHub API 밖으로 가는 리다이렉트", null),
                new GitHubClientException(Reason.UNAVAILABLE, "GitHub가 응답하지 않습니다(시도 3번)", null));
    }

    @ParameterizedTest
    @MethodSource("repoFailures")
    void syncRepo_repoFailure_countsItThenRethrowsWithoutSaving(GitHubClientException failure) {
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willThrow(failure);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID)).isSameAs(failure);

        then(ossIssueSyncSaver).should().recordFailure(REPO_ID);
        then(ossIssueSyncSaver).should(never()).save(any(), any(), any());
    }

    @Test
    void syncRepo_countingRepoFailureFails_rethrowsGitHubFailureWithThatFailureSuppressed() {
        GitHubClientException failure = new GitHubClientException(Reason.REJECTED, 404, "GitHub 요청 실패(상태 404)", null);
        CannotAcquireLockException lockTimeout = new CannotAcquireLockException("Lock wait timeout exceeded");
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willThrow(failure);
        willThrow(lockTimeout).given(ossIssueSyncSaver).recordFailure(REPO_ID);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID))
                .isSameAs(failure)
                .hasSuppressedException(lockTimeout);
    }

    static Stream<RuntimeException> clashesWithAnotherSync() {
        return Stream.of(
                new DataIntegrityViolationException("Duplicate entry for key 'uk_oss_issue_github_issue_id'"),
                new CannotAcquireLockException("Deadlock found when trying to get lock"),
                new PessimisticLockingFailureException("Lock wait timeout exceeded"));
    }

    @ParameterizedTest
    @MethodSource("clashesWithAnotherSync")
    void syncRepo_savingClashesWithAnotherSync_savesOnceMoreWithoutReadingGitHubAgain(RuntimeException clash) {
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW))
                .willThrow(clash)
                .willReturn(SAVED);

        assertThat(ossIssueSyncService.syncRepo(REPO_ID)).isEqualTo(SAVED);

        then(ossIssueSyncSaver).should(times(2)).save(REPO_ID, LISTED, NOW);
        then(gitHubClient).should(times(1)).listOpenIssues(any(), any(), any(), any());
    }

    @Test
    void syncRepo_savingFailsAgainAfterRetry_throwsSecondFailureWithFirstSuppressedWithoutThirdTry() {
        CannotAcquireLockException first = new CannotAcquireLockException("Deadlock found when trying to get lock");
        DataIntegrityViolationException again = new DataIntegrityViolationException("uk_oss_issue_github_issue_id");
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW))
                .willThrow(first)
                .willThrow(again);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID))
                .isSameAs(again)
                .hasSuppressedException(first);

        then(ossIssueSyncSaver).should(times(2)).save(REPO_ID, LISTED, NOW);
    }

    @Test
    void syncRepo_savingFailsForOtherReason_throwsItWithoutSavingAgain() {
        BusinessException repoGone = new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND);
        givenActiveRepo();
        given(ossRepoSyncStateRepository.findByRepoId(REPO_ID)).willReturn(Optional.of(stateReadKeepingEtag()));
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(LISTED);
        given(ossIssueSyncSaver.save(REPO_ID, LISTED, NOW)).willThrow(repoGone);

        assertThatThrownBy(() -> ossIssueSyncService.syncRepo(REPO_ID)).isSameAs(repoGone);

        then(ossIssueSyncSaver).should(times(1)).save(REPO_ID, LISTED, NOW);
    }

    private void givenActiveRepo() {
        given(ossRepoRepository.findByIdAndStatus(REPO_ID, OssRepoStatus.ACTIVE)).willReturn(Optional.of(repo));
    }

    private OssRepoSyncState newState() {
        return OssRepoSyncState.create(repo);
    }

    private OssRepoSyncState stateReadKeepingEtag() {
        OssRepoSyncState state = newState();
        state.recordRead(LATEST_UPDATE, ETAG, NOW.minusMinutes(30));
        return state;
    }
}
