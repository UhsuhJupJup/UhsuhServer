package uhsuhjupjup.backend.oss.github.application;

public class GitHubCredentialsMissingException extends GitHubClientException {

    public GitHubCredentialsMissingException() {
        super(Reason.NOT_CONFIGURED, "GitHub 토큰이 설정되지 않아 요청을 보내지 않았습니다", null);
    }
}
