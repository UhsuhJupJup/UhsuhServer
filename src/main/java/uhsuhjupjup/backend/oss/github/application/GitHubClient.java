package uhsuhjupjup.backend.oss.github.application;

import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueListResult;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueLookup;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubRepo;

import java.time.LocalDateTime;
import java.util.Optional;

public interface GitHubClient {

    Optional<GitHubRepo> findRepo(String owner, String name);

    GitHubIssueListResult listOpenIssues(String owner, String name, String previousEtag, LocalDateTime updatedSince);

    GitHubIssueLookup findIssue(long repositoryId, int number);
}
