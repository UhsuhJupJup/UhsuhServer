package uhsuhjupjup.backend.oss.github.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentials;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GitHubCredentialsBindingTest {

    private static final String TOKEN = "ghp_bindingTestToken";

    @Test
    void envToken_bindsToBearerHeaderWithoutBeingLogged(CapturedOutput output) {
        runnerWithEnv(Map.of("OSS_GITHUB_TOKEN", TOKEN)).run(context -> {
            GitHubCredentials credentials = context.getBean(GitHubCredentials.class);

            assertThat(credentials.isConfigured()).isTrue();
            assertThat(credentials.authorizationHeader()).isEqualTo("Bearer " + TOKEN);
        });

        assertThat(output).doesNotContain(TOKEN);
        assertThat(missingTokenWarnings(output)).isEmpty();
    }

    @Test
    void emptyEnvToken_startsContextAndWarnsOnce(CapturedOutput output) {
        runnerWithEnv(Map.of("OSS_GITHUB_TOKEN", "")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GitHubCredentials.class).isConfigured()).isFalse();
        });

        assertThat(missingTokenWarnings(output)).hasSize(1);
    }

    @Test
    void missingEnvToken_startsContextAndWarnsOnce(CapturedOutput output) {
        runnerWithEnv(Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GitHubCredentials.class).isConfigured()).isFalse();
        });

        assertThat(missingTokenWarnings(output)).hasSize(1);
    }

    private ApplicationContextRunner runnerWithEnv(Map<String, Object> env) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(UserConfigurations.of(PersonalAccessTokenGitHubCredentials.class));
    }

    private List<String> missingTokenWarnings(CapturedOutput output) {
        return output.getAll().lines()
                .filter(line -> line.contains("WARN") && line.contains("OSS_GITHUB_TOKEN"))
                .toList();
    }
}
