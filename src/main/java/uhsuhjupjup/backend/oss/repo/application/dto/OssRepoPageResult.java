package uhsuhjupjup.backend.oss.repo.application.dto;

import java.util.List;

public record OssRepoPageResult(List<OssRepoResult> items, OssRepoCursor nextCursor) {
}
