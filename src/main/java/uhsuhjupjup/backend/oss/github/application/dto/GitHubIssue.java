package uhsuhjupjup.backend.oss.github.application.dto;

import java.time.LocalDateTime;

public record GitHubIssue(
        long githubId,
        int number,
        String title,
        String body,
        boolean pullRequest,
        int assigneeCount,
        String authorLogin,
        String authorType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
