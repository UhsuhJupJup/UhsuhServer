package uhsuhjupjup.backend.oss.github.application.dto;

public record GitHubRepo(
        long githubId,
        String fullName,
        String description,
        String primaryLanguage,
        int stars,
        boolean isPrivate,
        boolean hasIssues,
        boolean archived) {
}
