package uhsuhjupjup.backend.oss.pipeline.grading.ui.dto;

import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult.StopReason;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueUngradableReason;

import java.util.Map;

public record AdminOssIssueGradingResponse(
        StopReason stopReason,
        int selected,
        int graded,
        int held,
        int notStarted,
        int sameBodySkipped,
        int failureLimitSkipped,
        int githubSkipped,
        Map<OssIssueUngradableReason, Integer> ungradable,
        Map<Reason, Integer> failed) {

    public static AdminOssIssueGradingResponse from(OssIssueGradingRunResult result) {
        return new AdminOssIssueGradingResponse(result.stopReason(), result.selected(), result.graded(),
                result.held(), result.notStarted(), result.sameBodySkipped(), result.failureLimitSkipped(),
                result.githubSkipped(), result.ungradable(), result.failed());
    }
}
