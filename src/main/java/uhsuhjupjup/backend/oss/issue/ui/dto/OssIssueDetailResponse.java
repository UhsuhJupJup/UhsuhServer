package uhsuhjupjup.backend.oss.issue.ui.dto;

import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDetailResult;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;

import java.time.LocalDateTime;

public record OssIssueDetailResponse(
        Long id,
        int number,
        String title,
        String githubUrl,
        LocalDateTime githubCreatedAt,
        RepoSummary repo,
        OssIssueDifficulty difficulty,
        Evidence evidence,
        String lang,
        String reason,
        String summary,
        LocalDateTime gradedAt) {

    public record RepoSummary(Long id, String fullName) {
    }

    public record Evidence(
            OssIssueEvidence problem,
            OssIssueEvidence reproduction,
            OssIssueEvidence cause,
            OssIssueEvidence fixDirection,
            boolean relatedPr) {
    }

    public static OssIssueDetailResponse from(OssIssueDetailResult result) {
        return new OssIssueDetailResponse(result.id(), result.number(), result.title(),
                GitHubIssueUrl.of(result.repoFullName(), result.number()),
                result.githubCreatedAt(),
                new RepoSummary(result.repoId(), result.repoFullName()),
                result.difficulty(),
                new Evidence(result.problem(), result.reproduction(), result.cause(), result.fixDirection(),
                        result.relatedPr()),
                result.language().code(), result.reason(), result.summary(), result.gradedAt());
    }
}
