package uhsuhjupjup.backend.oss.repo.ui.dto;

import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;

import java.util.List;

public record AdminOssRepoResponse(
        Long id,
        Long githubId,
        String fullName,
        String description,
        String primaryLanguage,
        int stars,
        OssRepoStatus status,
        List<OssCategoryResponse> categories) {

    public static AdminOssRepoResponse from(OssRepoResult result) {
        return new AdminOssRepoResponse(result.id(), result.githubId(), result.fullName(), result.description(),
                result.primaryLanguage(), result.stars(), result.status(),
                result.categories().stream().map(OssCategoryResponse::from).toList());
    }
}
