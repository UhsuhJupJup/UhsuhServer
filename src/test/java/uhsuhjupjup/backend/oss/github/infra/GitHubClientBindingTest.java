package uhsuhjupjup.backend.oss.github.infra;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentialsMissingException;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubClientBindingTest {

    private static final String TOKEN = "ghp_clientBinding";
    private static final String INVALID_VALUE = "::invalid::";

    private MockGitHubServer github;

    @BeforeEach
    void setUp() throws IOException {
        github = new MockGitHubServer("127.0.0.1");
    }

    @AfterEach
    void tearDown() {
        github.stop();
    }

    @Test
    void envValues_reachClient() {
        github.respond(MockGitHubServer::stall);
        Map<String, Object> env = Map.of(
                "OSS_GITHUB_TOKEN", TOKEN,
                "OSS_GITHUB_API_BASE_URL", github.url(""),
                "OSS_GITHUB_READ_TIMEOUT", "PT0.3S",
                "OSS_GITHUB_MAX_ATTEMPTS", "2",
                "OSS_GITHUB_RETRY_BACKOFF", "PT0.01S");

        runnerWithEnv(env).run(context -> {
            GitHubClient client = context.getBean(GitHubClient.class);
            long startedAt = System.nanoTime();

            assertThatThrownBy(() -> client.findRepo("octocat", "Hello-World"))
                    .isInstanceOfSatisfying(GitHubClientException.class,
                            e -> assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE));
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(5));
        });

        assertThat(github.requests())
                .hasSize(2)
                .allSatisfy(request -> assertThat(request.header("Authorization")).isEqualTo("Bearer " + TOKEN));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "OSS_GITHUB_API_BASE_URL",
            "OSS_GITHUB_CONNECT_TIMEOUT",
            "OSS_GITHUB_READ_TIMEOUT",
            "OSS_GITHUB_MAX_ATTEMPTS",
            "OSS_GITHUB_RETRY_BACKOFF"
    })
    void eachEnvName_isReadByClient(String envName) {
        runnerWithEnv(Map.of(envName, INVALID_VALUE)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().hasStackTraceContaining(INVALID_VALUE);
        });
    }

    @Test
    void withoutEnv_startsWithDefaultsAndSendsNothing() {
        runnerWithEnv(Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            GitHubClient client = context.getBean(GitHubClient.class);

            assertThatThrownBy(() -> client.findRepo("octocat", "Hello-World"))
                    .isInstanceOf(GitHubCredentialsMissingException.class);
        });
    }

    private ApplicationContextRunner runnerWithEnv(Map<String, Object> env) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
                .withConfiguration(UserConfigurations.of(
                        PersonalAccessTokenGitHubCredentials.class, RestGitHubClient.class));
    }
}
