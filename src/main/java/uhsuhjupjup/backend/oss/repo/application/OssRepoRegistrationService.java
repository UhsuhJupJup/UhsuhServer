package uhsuhjupjup.backend.oss.repo.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubRepo;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.util.List;
import java.util.OptionalInt;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssRepoRegistrationService {

    private static final char OWNER_NAME_SEPARATOR = '/';
    private static final String NO_STATUS = "없음";

    private final GitHubClient gitHubClient;
    private final OssRepoRepository ossRepoRepository;
    private final OssRepoSaver ossRepoSaver;

    public OssRepoResult register(String fullName) {
        GitHubRepo found = findOnGitHub(fullName);
        checkRegistrable(found);
        OssRepo repo = OssRepo.create(found.githubId(), found.fullName(), found.description(),
                found.primaryLanguage(), found.stars());
        if (isRegistered(repo)) {
            throw new BusinessException(ErrorCode.OSS_REPO_ALREADY_EXISTS);
        }
        return OssRepoResult.of(saveNew(repo), List.of());
    }

    private GitHubRepo findOnGitHub(String fullName) {
        int separator = fullName.indexOf(OWNER_NAME_SEPARATOR);
        String owner = fullName.substring(0, separator);
        String name = fullName.substring(separator + 1);
        try {
            return gitHubClient.findRepo(owner, name)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));
        } catch (GitHubClientException e) {
            log.warn("레포 등록을 위한 GitHub 조회 실패: {} (이유 {}, 상태 {}) {}",
                    fullName, e.getReason(), statusOf(e), e.getMessage());
            throw new BusinessException(errorCodeFor(e.getReason()));
        }
    }

    private void checkRegistrable(GitHubRepo repo) {
        if (repo.isPrivate()) {
            throw new BusinessException(ErrorCode.OSS_REPO_PRIVATE);
        }
        if (!repo.hasIssues()) {
            throw new BusinessException(ErrorCode.OSS_REPO_ISSUES_DISABLED);
        }
        if (repo.archived()) {
            throw new BusinessException(ErrorCode.OSS_REPO_ARCHIVED);
        }
    }

    private boolean isRegistered(OssRepo repo) {
        return ossRepoRepository.findByGithubId(repo.getGithubId()).isPresent()
                || ossRepoRepository.findByFullNameKey(repo.getFullNameKey()).isPresent();
    }

    private OssRepo saveNew(OssRepo repo) {
        try {
            return ossRepoSaver.save(repo);
        } catch (DataIntegrityViolationException e) {
            if (isRegistered(repo)) {
                throw new BusinessException(ErrorCode.OSS_REPO_ALREADY_EXISTS);
            }
            throw e;
        }
    }

    private static ErrorCode errorCodeFor(Reason reason) {
        if (reason == Reason.NOT_CONFIGURED) {
            return ErrorCode.GITHUB_TOKEN_REQUIRED;
        }
        return ErrorCode.GITHUB_UNAVAILABLE;
    }

    private static String statusOf(GitHubClientException e) {
        OptionalInt status = e.getStatusCode();
        return status.isPresent() ? String.valueOf(status.getAsInt()) : NO_STATUS;
    }
}
