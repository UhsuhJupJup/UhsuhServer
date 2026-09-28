package uhsuhjupjup.backend.oss.repo.ui.dto;

import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;

import java.util.List;

public record OssRepoDetailResponse(
        Long id,
        String fullName,
        String description,
        String primaryLanguage,
        int stars,
        List<OssCategoryResponse> categories,
        String githubUrl) {

    private static final String GITHUB_URL_PREFIX = "https://github.com/";

    public static OssRepoDetailResponse from(OssRepoResult result) {
        return new OssRepoDetailResponse(result.id(), result.fullName(), result.description(),
                result.primaryLanguage(), result.stars(),
                result.categories().stream().map(OssCategoryResponse::from).toList(),
                GITHUB_URL_PREFIX + result.fullName());
    }
}
