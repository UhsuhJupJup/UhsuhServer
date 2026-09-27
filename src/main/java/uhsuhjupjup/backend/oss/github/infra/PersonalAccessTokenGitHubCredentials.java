package uhsuhjupjup.backend.oss.github.infra;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentials;

@Slf4j
@Component
class PersonalAccessTokenGitHubCredentials implements GitHubCredentials {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String token;

    PersonalAccessTokenGitHubCredentials(@Value("${oss.github.token:}") String token) {
        this.token = token.strip();
    }

    @PostConstruct
    void warnIfMissing() {
        if (!isConfigured()) {
            log.warn("GitHub 토큰(OSS_GITHUB_TOKEN)이 없어 OSS 수집이 꺼진다");
        }
    }

    @Override
    public boolean isConfigured() {
        return !token.isEmpty();
    }

    @Override
    public String authorizationHeader() {
        if (!isConfigured()) {
            throw new IllegalStateException("GitHub 토큰이 설정되지 않았습니다");
        }
        return BEARER_PREFIX + token;
    }

    @Override
    public String toString() {
        return "PersonalAccessTokenGitHubCredentials(configured=" + isConfigured() + ")";
    }
}
