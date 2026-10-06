package uhsuhjupjup.backend.oss.pipeline.sync.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssue;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueListResult;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.pipeline.sync.application.dto.OssIssueSyncResult;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssueAuthorType;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter.ExclusionReason;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssRepoSyncState;
import uhsuhjupjup.backend.oss.pipeline.sync.infra.OssRepoSyncStateRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OssIssueSyncSaver {

    private final OssRepoRepository ossRepoRepository;
    private final OssIssueRepository ossIssueRepository;
    private final OssRepoSyncStateRepository ossRepoSyncStateRepository;

    @Transactional
    public OssRepoSyncState register(Long repoId) {
        return ossRepoSyncStateRepository.save(OssRepoSyncState.create(ossRepoRepository.getReferenceById(repoId)));
    }

    @Transactional
    public void recordFailure(Long repoId) {
        ossRepoSyncStateRepository.findForUpdateByRepoId(repoId).ifPresent(OssRepoSyncState::recordFailure);
    }

    @Transactional
    public OssIssueSyncResult save(Long repoId, GitHubIssueListResult listed, LocalDateTime syncedAt) {
        OssRepoSyncState state = ossRepoSyncStateRepository.findForUpdateByRepoId(repoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND));
        if (listed.notModified()) {
            state.recordNotModified(syncedAt);
            return OssIssueSyncResult.unchanged();
        }
        Map<ExclusionReason, Integer> excluded = new EnumMap<>(ExclusionReason.class);
        List<GitHubIssue> kept = new ArrayList<>();
        for (GitHubIssue issue : listed.issues()) {
            reasonToExclude(issue).ifPresentOrElse(
                    reason -> excluded.merge(reason, 1, Integer::sum),
                    () -> kept.add(issue));
        }
        Stored stored = store(repoId, kept);
        recordRead(state, listed, syncedAt);
        return new OssIssueSyncResult(false, incompleteReasonOf(listed), listed.issues().size(), excluded,
                stored.created(), stored.bodyChanged(), stored.bodyUnchanged());
    }

    private static Optional<ExclusionReason> reasonToExclude(GitHubIssue issue) {
        return OssIssuePrefilter.reasonToExclude(issue.pullRequest(), issue.assigneeCount(), issue.authorLogin(),
                OssIssueAuthorType.from(issue.authorType()));
    }

    private Stored store(Long repoId, List<GitHubIssue> issues) {
        if (issues.isEmpty()) {
            return new Stored(0, 0, 0);
        }
        OssRepo repo = ossRepoRepository.getReferenceById(repoId);
        Map<Long, OssIssue> storedByGithubId = ossIssueRepository
                .findAllByGithubIssueIdIn(issues.stream().map(GitHubIssue::githubId).toList()).stream()
                .collect(Collectors.toMap(OssIssue::getGithubIssueId, Function.identity()));
        List<OssIssue> created = new ArrayList<>();
        int bodyChanged = 0;
        for (GitHubIssue issue : issues) {
            OssIssue existing = storedByGithubId.get(issue.githubId());
            if (existing == null) {
                created.add(OssIssue.create(repo, issue.githubId(), issue.number(), issue.title(), issue.body(),
                        issue.createdAt()));
            } else if (refreshChangesBody(existing, repo, issue)) {
                bodyChanged++;
            }
        }
        created.sort(Comparator.comparing(OssIssue::getGithubIssueId));
        ossIssueRepository.saveAll(created);
        return new Stored(created.size(), bodyChanged, issues.size() - created.size() - bodyChanged);
    }

    private static boolean refreshChangesBody(OssIssue existing, OssRepo repo, GitHubIssue issue) {
        String bodyHashBefore = existing.getBodyHash();
        existing.refresh(repo, issue.number(), issue.title(), issue.body());
        return !bodyHashBefore.equals(existing.getBodyHash());
    }

    private static void recordRead(OssRepoSyncState state, GitHubIssueListResult listed, LocalDateTime syncedAt) {
        LocalDateTime latestIssueUpdatedAt = listed.issues().stream()
                .map(GitHubIssue::updatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (listed.incompleteReason() == GitHubIssueListResult.IncompleteReason.PAGE_UNAVAILABLE) {
            state.recordReadCutByFailure(latestIssueUpdatedAt, syncedAt);
            return;
        }
        state.recordRead(latestIssueUpdatedAt, listed.etag(), syncedAt);
    }

    private static OssIssueSyncResult.IncompleteReason incompleteReasonOf(GitHubIssueListResult listed) {
        if (listed.complete()) {
            return null;
        }
        return switch (listed.incompleteReason()) {
            case PAGE_LIMIT -> OssIssueSyncResult.IncompleteReason.PAGE_LIMIT;
            case PAGE_UNAVAILABLE -> OssIssueSyncResult.IncompleteReason.PAGE_UNAVAILABLE;
        };
    }

    private record Stored(int created, int bodyChanged, int bodyUnchanged) {
    }
}
