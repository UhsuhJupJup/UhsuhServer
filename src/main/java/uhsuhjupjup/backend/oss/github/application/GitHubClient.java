package uhsuhjupjup.backend.oss.github.application;

import uhsuhjupjup.backend.oss.github.application.dto.GitHubRepo;

import java.util.Optional;

public interface GitHubClient {

    Optional<GitHubRepo> findRepo(String owner, String name);
}
