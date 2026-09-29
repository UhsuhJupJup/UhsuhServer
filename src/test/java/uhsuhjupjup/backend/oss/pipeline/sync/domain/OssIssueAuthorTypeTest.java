package uhsuhjupjup.backend.oss.pipeline.sync.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OssIssueAuthorTypeTest {

    @Test
    void from_githubBotType_isBot() {
        assertThat(OssIssueAuthorType.from("Bot")).isEqualTo(OssIssueAuthorType.BOT);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"User", "Organization", "Unknown"})
    void from_anythingButBot_isOther(String githubType) {
        assertThat(OssIssueAuthorType.from(githubType)).isEqualTo(OssIssueAuthorType.OTHER);
    }
}
