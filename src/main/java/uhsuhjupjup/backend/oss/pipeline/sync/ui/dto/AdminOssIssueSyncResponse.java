package uhsuhjupjup.backend.oss.pipeline.sync.ui.dto;

import uhsuhjupjup.backend.oss.pipeline.sync.application.dto.OssIssueSyncResult;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter.ExclusionReason;

import java.util.Map;

public record AdminOssIssueSyncResponse(
        boolean notModified,
        OssIssueSyncResult.IncompleteReason incompleteReason,
        int received,
        Map<ExclusionReason, Integer> excluded,
        int created,
        int bodyChanged,
        int bodyUnchanged) {

    public static AdminOssIssueSyncResponse from(OssIssueSyncResult result) {
        return new AdminOssIssueSyncResponse(result.notModified(), result.incompleteReason(), result.received(),
                result.excluded(), result.created(), result.bodyChanged(), result.bodyUnchanged());
    }
}
