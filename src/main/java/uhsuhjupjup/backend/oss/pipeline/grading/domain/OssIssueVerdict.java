package uhsuhjupjup.backend.oss.pipeline.grading.domain;

import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;

public record OssIssueVerdict(
        OssIssueDifficulty difficulty,
        OssIssueEvidence problem,
        OssIssueEvidence reproduction,
        OssIssueEvidence cause,
        OssIssueEvidence fixDirection,
        boolean relatedPr,
        OssIssueGradeExclusion exclusion,
        String reasonKo,
        String reasonEn,
        String summaryKo,
        String summaryEn) {
}
