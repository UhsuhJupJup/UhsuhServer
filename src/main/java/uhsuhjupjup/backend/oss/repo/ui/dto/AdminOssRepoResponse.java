package uhsuhjupjup.backend.oss.repo.ui.dto;

import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;

public record AdminOssRepoResponse(
        Long id,
        Long githubId,
        String fullName,
        String description,
        String primaryLanguage,
        int stars,
        OssRepoStatus status) {

    public static AdminOssRepoResponse from(OssRepoResult result) {
        return new AdminOssRepoResponse(result.id(), result.githubId(), result.fullName(), result.description(),
                result.primaryLanguage(), result.stars(), result.status());
    }
}
