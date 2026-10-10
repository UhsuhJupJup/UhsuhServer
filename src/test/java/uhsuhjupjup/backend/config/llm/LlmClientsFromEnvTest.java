package uhsuhjupjup.backend.config.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.MessageCreateParams;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.status;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.trickle;

@Isolated
@ExtendWith(OutputCaptureExtension.class)
class LlmClientsFromEnvTest {

    private static final String FAKE_API_KEY = "fake-api-key";
    private static final String FAKE_AUTH_TOKEN = "fake-auth-token";
    private static final String FAKE_WEBHOOK_KEY = "fake-webhook-key";
    private static final String BASE_URL_PROPERTY = "anthropic.baseUrl";
    private static final String OPENAI_BASE_URL_PROPERTY = "openai.baseUrl";
    private static final List<String> OVERRIDDEN_PROPERTIES = List.of(
            BASE_URL_PROPERTY, "anthropic.apiKey", "anthropic.authToken", "anthropic.webhookSigningKey",
            OPENAI_BASE_URL_PROPERTY, "openai.apiKey");
    private static final String ERROR_BODY = """
            {"type": "error", "error": {"type": "api_error", "message": "boom"}}
            """;
    private static final String UNAUTHORIZED_BODY = """
            {"type": "error", "error": {"type": "authentication_error", "message": "invalid x-api-key"}}
            """;
    private static final String OK_BODY = """
            {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-haiku-4-5",
             "content": [{"type": "text", "text": "pong"}], "stop_reason": "end_turn", "stop_sequence": null,
             "usage": {"input_tokens": 1, "output_tokens": 1}}
            """;
    private static final String OPENAI_OK_BODY = """
            {"id": "chatcmpl-test", "object": "chat.completion", "created": 1700000000, "model": "gpt-4o-mini",
             "choices": [{"index": 0, "message": {"role": "assistant", "content": "pong", "refusal": null},
                          "finish_reason": "stop", "logprobs": null}],
             "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}
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

    @ParameterizedTest
    @ValueSource(strings = {FAKE_API_KEY + "\n", FAKE_API_KEY + "\r\n", " \t" + FAKE_API_KEY + " \n"})
    void anthropic_keyWithSurroundingWhitespace_sendsTheStrippedKey(String apiKey) {
        server.respondInOrder(status(200, OK_BODY));

        assertThatCode(() -> ping(anthropicFromFakeEnvironment(apiKey))).doesNotThrowAnyException();

        assertThat(server.header(0, "X-Api-Key")).isEqualTo(FAKE_API_KEY);
    }

    @Test
    void anthropic_keyEndingInLineBreak_failsWithoutTheKeyInTheErrorOrOutput(CapturedOutput output) {
        server.respondInOrder(status(401, UNAUTHORIZED_BODY));

        Throwable failure = catchThrowable(() -> ping(anthropicFromFakeEnvironment(FAKE_API_KEY + "\n")));

        assertThat(failure).isInstanceOf(UnauthorizedException.class);
        assertThat(stackTraceOf(failure)).doesNotContain(FAKE_API_KEY);
        assertThat(output).doesNotContain(FAKE_API_KEY);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void anthropic_emptyKey_isStillSentEmpty() {
        server.respondInOrder(status(401, UNAUTHORIZED_BODY));

        Throwable failure = catchThrowable(() -> ping(anthropicFromFakeEnvironment("")));

        assertThat(failure).isInstanceOf(UnauthorizedException.class);
        assertThat(server.header(0, "X-Api-Key")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {FAKE_API_KEY + "\n", FAKE_API_KEY + "\r\n", " \t" + FAKE_API_KEY + " \n"})
    void openAi_keyWithSurroundingWhitespace_sendsTheStrippedKey(String apiKey) {
        server.respondInOrder(status(200, OPENAI_OK_BODY));

        assertThatCode(() -> ping(openAiFromFakeEnvironment(apiKey))).doesNotThrowAnyException();

        assertThat(server.header(0, "Authorization")).isEqualTo("Bearer " + FAKE_API_KEY);
    }

    @Test
    void openAi_emptyKey_stillFailsToBuild() {
        assertThatThrownBy(() -> openAiFromFakeEnvironment("")).isInstanceOf(IllegalStateException.class);

        assertThat(server.requestCount()).isZero();
    }

    private AnthropicClient anthropicFromFakeEnvironment() {
        return anthropicFromFakeEnvironment(FAKE_API_KEY);
    }

    private AnthropicClient anthropicFromFakeEnvironment(String apiKey) {
        Map<String, String> fakeEnvironment = Map.of(
                BASE_URL_PROPERTY, server.baseUrl(),
                "anthropic.apiKey", apiKey,
                "anthropic.authToken", FAKE_AUTH_TOKEN,
                "anthropic.webhookSigningKey", FAKE_WEBHOOK_KEY);
        return withProperties(fakeEnvironment, () -> LlmClients.anthropic(LIMITS));
    }

    private OpenAIClient openAiFromFakeEnvironment(String apiKey) {
        Map<String, String> fakeEnvironment = Map.of(
                OPENAI_BASE_URL_PROPERTY, server.baseUrl(),
                "openai.apiKey", apiKey);
        return withProperties(fakeEnvironment, () -> LlmClients.openAi(LIMITS));
    }

    private static <T> T withProperties(Map<String, String> properties, Supplier<T> action) {
        Map<String, String> previous = currentValues();
        try {
            properties.forEach(System::setProperty);
            return action.get();
        } finally {
            previous.forEach(LlmClientsFromEnvTest::restore);
        }
    }

    private static String stackTraceOf(Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        return trace.toString();
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

    private static void ping(OpenAIClient client) {
        try {
            client.chat().completions().create(ChatCompletionCreateParams.builder()
                    .model("gpt-4o-mini")
                    .addUserMessage("ping")
                    .build());
        } finally {
            client.close();
        }
    }
}
