package uhsuhjupjup.backend.oss.repo.application.dto;

import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;

public record OssRepoResult(
        Long id,
        Long githubId,
        String fullName,
        String description,
        String primaryLanguage,
        int stars,
        OssRepoStatus status) {

    public static OssRepoResult from(OssRepo repo) {
        return new OssRepoResult(repo.getId(), repo.getGithubId(), repo.getFullName(), repo.getDescription(),
                repo.getPrimaryLanguage(), repo.getStars(), repo.getStatus());
    }
}
