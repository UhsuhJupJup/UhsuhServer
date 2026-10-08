package uhsuhjupjup.backend.oss.issue.application.dto;

import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.LocalDateTime;

public record OssIssueResult(
        Long id,
        int number,
        String title,
        LocalDateTime githubCreatedAt,
        String repoFullName,
        OssIssueDifficulty difficulty,
        OssIssueEvidence problem,
        OssIssueEvidence reproduction,
        OssIssueEvidence cause,
        OssIssueEvidence fixDirection,
        boolean relatedPr,
        String summary,
        LocalDateTime gradedAt) {

    public static OssIssueResult of(OssRepo repo, OssIssueGrade grade, OssIssueLanguage language) {
        OssIssue issue = grade.getIssue();
        return new OssIssueResult(issue.getId(), issue.getNumber(), issue.getTitle(), issue.getGithubCreatedAt(),
                repo.getFullName(), grade.getDifficulty(), grade.getProblem(), grade.getReproduction(),
                grade.getCause(), grade.getFixDirection(), grade.isRelatedPr(),
                language.choose(grade.getSummaryKo(), grade.getSummaryEn()), grade.getCreatedAt());
    }
}
