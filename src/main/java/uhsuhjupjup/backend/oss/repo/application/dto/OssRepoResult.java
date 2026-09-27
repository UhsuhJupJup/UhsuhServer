package uhsuhjupjup.backend.oss.repo.application.dto;

import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;

import java.util.List;

public record OssRepoResult(
        Long id,
        Long githubId,
        String fullName,
        String description,
        String primaryLanguage,
        int stars,
        OssRepoStatus status,
        List<OssCategoryResult> categories) {

    public static OssRepoResult of(OssRepo repo, List<OssCategory> categories) {
        return new OssRepoResult(repo.getId(), repo.getGithubId(), repo.getFullName(), repo.getDescription(),
                repo.getPrimaryLanguage(), repo.getStars(), repo.getStatus(),
                categories.stream().map(OssCategoryResult::from).toList());
    }
}
