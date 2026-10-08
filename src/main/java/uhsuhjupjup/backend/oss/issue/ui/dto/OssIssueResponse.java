package uhsuhjupjup.backend.oss.issue.ui.dto;

import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueResult;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;

import java.time.LocalDateTime;

public record OssIssueResponse(
        Long id,
        int number,
        String title,
        String githubUrl,
        LocalDateTime githubCreatedAt,
        OssIssueDifficulty difficulty,
        OssIssueDetailResponse.Evidence evidence,
        String summary,
        LocalDateTime gradedAt) {

    public static OssIssueResponse from(OssIssueResult result) {
        return new OssIssueResponse(result.id(), result.number(), result.title(),
                GitHubIssueUrl.of(result.repoFullName(), result.number()),
                result.githubCreatedAt(), result.difficulty(),
                new OssIssueDetailResponse.Evidence(result.problem(), result.reproduction(), result.cause(),
                        result.fixDirection(), result.relatedPr()),
                result.summary(), result.gradedAt());
    }
}
