package uhsuhjupjup.backend.oss.github.application;

public interface GitHubCredentials {

    boolean isConfigured();

    String authorizationHeader();
}
