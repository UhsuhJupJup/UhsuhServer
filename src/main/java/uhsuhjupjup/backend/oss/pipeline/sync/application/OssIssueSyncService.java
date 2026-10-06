package uhsuhjupjup.backend.oss.pipeline.sync.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
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
import java.util.EnumSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssIssueSyncService {

    private static final char OWNER_NAME_SEPARATOR = '/';
    private static final String SYNC_LOCK_NAME_PREFIX = "ossIssueSync-";
    private static final Duration SYNC_LOCK_AT_MOST_FOR = Duration.ofMinutes(20);
    private static final Set<Reason> FAILURES_BEYOND_REPO =
            EnumSet.of(Reason.NOT_CONFIGURED, Reason.UNAUTHORIZED, Reason.RATE_LIMITED);

    private final GitHubClient gitHubClient;
    private final OssRepoRepository ossRepoRepository;
    private final OssRepoSyncStateRepository ossRepoSyncStateRepository;
    private final OssIssueSyncSaver ossIssueSyncSaver;
    private final LockProvider lockProvider;
    private final Clock clock;

    public OssIssueSyncResult syncRepo(Long repoId) {
        OssRepo repo = ossRepoRepository.findByIdAndStatus(repoId, OssRepoStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND));
        SimpleLock syncLock = acquireSyncLock(repo);
        try {
            return syncHoldingLock(repo);
        } finally {
            releaseSyncLock(syncLock, repo);
        }
    }

    private SimpleLock acquireSyncLock(OssRepo repo) {
        LockConfiguration lockConfiguration = new LockConfiguration(clock.instant(),
                SYNC_LOCK_NAME_PREFIX + repo.getId(), SYNC_LOCK_AT_MOST_FOR, Duration.ZERO);
        return lockProvider.lock(lockConfiguration).orElseThrow(() -> {
            log.info("오픈소스 레포 {} 이슈 수집이 이미 진행 중이라 시작하지 않음", repo.getFullName());
            return new BusinessException(ErrorCode.OSS_ISSUE_SYNC_IN_PROGRESS);
        });
    }

    private void releaseSyncLock(SimpleLock lock, OssRepo repo) {
        try {
            lock.unlock();
        } catch (RuntimeException e) {
            log.warn("오픈소스 레포 {} 이슈 수집 잠금을 풀지 못함, 잡은 지 {}분 뒤 저절로 풀림",
                    repo.getFullName(), SYNC_LOCK_AT_MOST_FOR.toMinutes(), e);
        }
    }

    private OssIssueSyncResult syncHoldingLock(OssRepo repo) {
        Long repoId = repo.getId();
        OssRepoSyncState state = ossRepoSyncStateRepository.findByRepoId(repoId)
                .orElseGet(() -> register(repoId));
        LocalDateTime now = LocalDateTime.now(clock);
        GitHubIssueListResult listed = listOpenIssues(repo, state.getEtag(), state.issuesUpdatedSince(now));
        OssIssueSyncResult result = save(repoId, listed, now);
        log.info("오픈소스 레포 {} 이슈 수집: 바뀐 것 없음 {}, 끝까지 {}, 받음 {}, 제외 {}, 새로 {}, 본문 바뀜 {}, 본문 그대로 {}",
                repo.getFullName(), result.notModified(), result.complete(), result.received(), result.excluded(),
                result.created(), result.bodyChanged(), result.bodyUnchanged());
        return result;
    }

    private OssRepoSyncState register(Long repoId) {
        try {
            return ossIssueSyncSaver.register(repoId);
        } catch (DataIntegrityViolationException e) {
            return ossRepoSyncStateRepository.findByRepoId(repoId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND));
        }
    }

    private GitHubIssueListResult listOpenIssues(OssRepo repo, String etag, LocalDateTime since) {
        String fullName = repo.getFullName();
        int separator = fullName.indexOf(OWNER_NAME_SEPARATOR);
        try {
            return gitHubClient.listOpenIssues(fullName.substring(0, separator), fullName.substring(separator + 1),
                    etag, since);
        } catch (GitHubClientException e) {
            if (FAILURES_BEYOND_REPO.contains(e.getReason())) {
                log.warn("오픈소스 레포 {} 이슈 수집을 레포 밖의 이유로 멈춤(이유 {}): {}", fullName, e.getReason(), e.getMessage());
                throw e;
            }
            log.warn("오픈소스 레포 {} 이슈 수집 실패(이유 {}): {}", fullName, e.getReason(), e.getMessage());
            recordFailure(repo.getId(), e);
            throw e;
        }
    }

    private void recordFailure(Long repoId, GitHubClientException failure) {
        try {
            ossIssueSyncSaver.recordFailure(repoId);
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    private OssIssueSyncResult save(Long repoId, GitHubIssueListResult listed, LocalDateTime syncedAt) {
        try {
            return ossIssueSyncSaver.save(repoId, listed, syncedAt);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            log.info("오픈소스 레포 {} 이슈 저장이 다른 수집과 겹쳐({}) 한 번 더 저장함", repoId, e.getClass().getSimpleName());
            return saveAgain(repoId, listed, syncedAt, e);
        }
    }

    private OssIssueSyncResult saveAgain(Long repoId, GitHubIssueListResult listed, LocalDateTime syncedAt,
                                         RuntimeException firstFailure) {
        try {
            return ossIssueSyncSaver.save(repoId, listed, syncedAt);
        } catch (RuntimeException e) {
            e.addSuppressed(firstFailure);
            throw e;
        }
    }
}
