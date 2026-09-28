package uhsuhjupjup.backend.oss.repo.ui.dto;

import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoPageResult;

import java.util.List;

public record OssRepoPageResponse(List<OssRepoResponse> items, String nextCursor) {

    public static OssRepoPageResponse from(OssRepoPageResult result) {
        return new OssRepoPageResponse(
                result.items().stream().map(OssRepoResponse::from).toList(),
                result.nextCursor() == null ? null : result.nextCursor().encode());
    }
}
