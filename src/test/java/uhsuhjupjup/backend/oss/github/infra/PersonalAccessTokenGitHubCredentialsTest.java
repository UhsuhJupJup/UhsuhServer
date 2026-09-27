package uhsuhjupjup.backend.oss.github.infra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalAccessTokenGitHubCredentialsTest {

    private static final String TOKEN = "ghp_unitTestToken";

    @Test
    void withToken_isConfiguredAndSuppliesBearerHeader() {
        PersonalAccessTokenGitHubCredentials credentials = new PersonalAccessTokenGitHubCredentials(TOKEN);

        assertThat(credentials.isConfigured()).isTrue();
        assertThat(credentials.authorizationHeader()).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void withSurroundingWhitespace_stripsItFromHeader() {
        PersonalAccessTokenGitHubCredentials credentials =
                new PersonalAccessTokenGitHubCredentials("  " + TOKEN + "\n");

        assertThat(credentials.authorizationHeader()).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void withEmptyToken_isNotConfigured() {
        PersonalAccessTokenGitHubCredentials credentials = new PersonalAccessTokenGitHubCredentials("");

        assertThat(credentials.isConfigured()).isFalse();
    }

    @Test
    void withBlankToken_isNotConfigured() {
        PersonalAccessTokenGitHubCredentials credentials = new PersonalAccessTokenGitHubCredentials("   ");

        assertThat(credentials.isConfigured()).isFalse();
    }

    @Test
    void authorizationHeader_withoutToken_throws() {
        PersonalAccessTokenGitHubCredentials credentials = new PersonalAccessTokenGitHubCredentials("");

        assertThatThrownBy(credentials::authorizationHeader)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toString_doesNotExposeToken() {
        PersonalAccessTokenGitHubCredentials credentials = new PersonalAccessTokenGitHubCredentials(TOKEN);

        assertThat(credentials.toString()).doesNotContain(TOKEN);
    }
}
