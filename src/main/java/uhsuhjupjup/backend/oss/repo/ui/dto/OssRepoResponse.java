package uhsuhjupjup.backend.oss.repo.ui.dto;

import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;

import java.util.List;

public record OssRepoResponse(
        Long id,
        String fullName,
        String description,
        String primaryLanguage,
        int stars,
        List<OssCategoryResponse> categories) {

    public static OssRepoResponse from(OssRepoResult result) {
        return new OssRepoResponse(result.id(), result.fullName(), result.description(), result.primaryLanguage(),
                result.stars(), result.categories().stream().map(OssCategoryResponse::from).toList());
    }
}
