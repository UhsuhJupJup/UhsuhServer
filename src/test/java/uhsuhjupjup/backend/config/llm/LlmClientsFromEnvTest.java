package uhsuhjupjup.backend.config.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.models.messages.MessageCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.status;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.trickle;

@Isolated
class LlmClientsFromEnvTest {

    private static final String FAKE_API_KEY = "fake-api-key";
    private static final String FAKE_AUTH_TOKEN = "fake-auth-token";
    private static final String FAKE_WEBHOOK_KEY = "fake-webhook-key";
    private static final String BASE_URL_PROPERTY = "anthropic.baseUrl";
    private static final List<String> OVERRIDDEN_PROPERTIES = List.of(
            BASE_URL_PROPERTY, "anthropic.apiKey", "anthropic.authToken", "anthropic.webhookSigningKey");
    private static final String ERROR_BODY = """
            {"type": "error", "error": {"type": "api_error", "message": "boom"}}
            """;
    private static final String OK_BODY = """
            {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-haiku-4-5",
             "content": [{"type": "text", "text": "pong"}], "stop_reason": "end_turn", "stop_sequence": null,
             "usage": {"input_tokens": 1, "output_tokens": 1}}
            """;
    private static final LlmCallLimits LIMITS =
            new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(2));

    private MockLlmServer server;
    private Map<String, String> propertiesBefore;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockLlmServer();
        propertiesBefore = currentValues();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        assertThat(currentValues()).isEqualTo(propertiesBefore);
    }

    @Test
    void anthropic_serverErrorTwice_triesTwiceWithFakeCredentialsOnly() {
        server.respondInOrder(status(500, ERROR_BODY), status(500, ERROR_BODY), status(200, OK_BODY));

        Throwable failure = catchThrowable(() -> ping(anthropicFromFakeEnvironment()));

        assertThat(failure).isInstanceOf(InternalServerException.class);
        assertThat(server.requestCount()).isEqualTo(2);
        assertThat(server.header(0, "X-Api-Key")).isEqualTo(FAKE_API_KEY);
        assertThat(server.header(0, "Authorization")).isEqualTo("Bearer " + FAKE_AUTH_TOKEN);
    }

    @Test
    void anthropic_longRetryAfter_failsAtOnceWithoutRetrying() {
        server.respondInOrder(status(429, ERROR_BODY, Map.of("Retry-After", "30")), status(200, OK_BODY));

        Throwable failure = catchThrowable(() -> ping(anthropicFromFakeEnvironment()));
        Duration elapsed = server.sinceFirstRequest();

        assertThat(failure).isInstanceOf(LlmRetryAbortedException.class);
        assertThat(elapsed).isLessThan(LIMITS.maxRetryWait());
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void anthropic_okBodyTricklingPastCallTimeout_failsNearCallTimeout() {
        server.respondInOrder(trickle(200, OK_BODY, Duration.ofMillis(100), Duration.ofSeconds(6)));

        Throwable failure = catchThrowable(() -> ping(anthropicFromFakeEnvironment()));
        Duration elapsed = server.sinceFirstRequest();

        assertThat(failure).isInstanceOf(AnthropicInvalidDataException.class).hasCauseInstanceOf(IOException.class);
        assertThat(server.requestCount()).isEqualTo(1);
        assertThat(elapsed).isBetween(LIMITS.callTimeout().dividedBy(2), LIMITS.callTimeout().plusSeconds(1));
    }

    private AnthropicClient anthropicFromFakeEnvironment() {
        Map<String, String> fakeEnvironment = Map.of(
                BASE_URL_PROPERTY, server.baseUrl(),
                "anthropic.apiKey", FAKE_API_KEY,
                "anthropic.authToken", FAKE_AUTH_TOKEN,
                "anthropic.webhookSigningKey", FAKE_WEBHOOK_KEY);
        Map<String, String> previous = currentValues();
        try {
            fakeEnvironment.forEach(System::setProperty);
            return LlmClients.anthropic(LIMITS);
        } finally {
            previous.forEach(LlmClientsFromEnvTest::restore);
        }
    }

    private static Map<String, String> currentValues() {
        Map<String, String> values = new HashMap<>();
        OVERRIDDEN_PROPERTIES.forEach(key -> values.put(key, System.getProperty(key)));
        return values;
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }

    private static void ping(AnthropicClient client) {
        try {
            client.messages().create(MessageCreateParams.builder()
                    .model("claude-haiku-4-5")
                    .maxTokens(16L)
                    .addUserMessage("ping")
                    .build());
        } finally {
            client.close();
        }
    }
}
