package uhsuhjupjup.backend.oss.issue.application.dto;

import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.LocalDateTime;

public record OssIssueDetailResult(
        Long id,
        int number,
        String title,
        LocalDateTime githubCreatedAt,
        Long repoId,
        String repoFullName,
        OssIssueDifficulty difficulty,
        OssIssueEvidence problem,
        OssIssueEvidence reproduction,
        OssIssueEvidence cause,
        OssIssueEvidence fixDirection,
        boolean relatedPr,
        OssIssueLanguage language,
        String reason,
        String summary,
        LocalDateTime gradedAt) {

    public static OssIssueDetailResult of(OssIssue issue, OssIssueGrade grade, OssIssueLanguage language) {
        OssRepo repo = issue.getRepo();
        return new OssIssueDetailResult(issue.getId(), issue.getNumber(), issue.getTitle(),
                issue.getGithubCreatedAt(), repo.getId(), repo.getFullName(), grade.getDifficulty(),
                grade.getProblem(), grade.getReproduction(), grade.getCause(), grade.getFixDirection(),
                grade.isRelatedPr(), language, language.choose(grade.getReasonKo(), grade.getReasonEn()),
                language.choose(grade.getSummaryKo(), grade.getSummaryEn()), grade.getCreatedAt());
    }
}
