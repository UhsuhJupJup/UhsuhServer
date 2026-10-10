package uhsuhjupjup.backend.config.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.messages.MessageCreateParams;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.stall;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.status;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.trickle;

class LlmClientsRetryTest {

    private static final String API_KEY = "test-key";
    private static final String ERROR_BODY = """
            {"type": "error", "error": {"type": "api_error", "message": "boom"}}
            """;
    private static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(2);
    private static final LlmCallLimits LIMITS =
            new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(5), 1, MAX_RETRY_WAIT);
    private static final LlmCallLimits SHORT_CALL =
            new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(1), 1, MAX_RETRY_WAIT);
    private static final Duration TRICKLE_INTERVAL = Duration.ofMillis(100);
    private static final Duration TRICKLE_TOTAL = Duration.ofSeconds(6);

    private MockLlmServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockLlmServer();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @ParameterizedTest
    @MethodSource("sdkAndTransientStatus")
    void transientServerErrorThenOk_retriesOnceAndSucceeds(Sdk sdk, int status) {
        server.respondInOrder(status(status, ERROR_BODY), status(200, sdk.okBody));

        assertThat(sdk.call(server.baseUrl(), LIMITS)).isEqualTo(sdk.okId);
        assertThat(server.requestCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(Sdk.class)
    void serverErrorTwice_failsWithoutThirdRequest(Sdk sdk) {
        server.respondInOrder(status(500, ERROR_BODY), status(500, ERROR_BODY), status(200, sdk.okBody));

        assertThatThrownBy(() -> sdk.call(server.baseUrl(), LIMITS)).isInstanceOf(sdk.serverError);
        assertThat(server.requestCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(Sdk.class)
    void noRetryAllowed_failsAfterFirstRequest(Sdk sdk) {
        LlmCallLimits noRetry = new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(5), 0, MAX_RETRY_WAIT);
        server.respondInOrder(status(503, ERROR_BODY), status(200, sdk.okBody));

        assertThatThrownBy(() -> sdk.call(server.baseUrl(), noRetry)).isInstanceOf(sdk.serverError);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("sdkAndLongRetryAfter")
    void rateLimitedWithLongRetryAfter_failsAtOnceWithoutRetrying(Sdk sdk, String header, String value) {
        server.respondInOrder(status(429, ERROR_BODY, Map.of(header, value)), status(200, sdk.okBody));

        Throwable failure = catchThrowable(() -> sdk.call(server.baseUrl(), LIMITS));
        Duration elapsed = server.sinceFirstRequest();

        assertThat(failure).isInstanceOf(LlmRetryAbortedException.class);
        assertThat(elapsed).isLessThan(MAX_RETRY_WAIT);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("sdkAndShortRetryAfter")
    void rateLimitedWithShortRetryAfter_waitsThatLongThenRetries(Sdk sdk, String header, String value,
                                                                 Duration wait) {
        server.respondInOrder(status(429, ERROR_BODY, Map.of(header, value)), status(200, sdk.okBody));

        assertThat(sdk.call(server.baseUrl(), LIMITS)).isEqualTo(sdk.okId);
        assertThat(server.requestCount()).isEqualTo(2);
        assertThat(server.gapBetweenRequests(0, 1)).isBetween(wait, MAX_RETRY_WAIT);
    }

    @ParameterizedTest
    @EnumSource(Sdk.class)
    void readTimeoutEveryTime_failsWithinWorstCase(Sdk sdk) {
        LlmCallLimits impatient =
                new LlmCallLimits(Duration.ofSeconds(1), Duration.ofMillis(300), 1, MAX_RETRY_WAIT);
        server.respondInOrder(stall());

        Throwable failure = catchThrowable(() -> sdk.call(server.baseUrl(), impatient));
        Duration elapsed = server.sinceFirstRequest();

        assertThat(failure).isInstanceOf(sdk.ioError);
        assertThat(server.requestCount()).isEqualTo(2);
        assertThat(elapsed).isLessThan(worstCase(impatient));
    }

    @ParameterizedTest
    @EnumSource(Sdk.class)
    void okBodyTricklingPastCallTimeout_failsOnceNearCallTimeout(Sdk sdk) {
        server.respondInOrder(trickle(200, sdk.okBody, TRICKLE_INTERVAL, TRICKLE_TOTAL));

        Throwable failure = catchThrowable(() -> sdk.call(server.baseUrl(), SHORT_CALL));
        Duration elapsed = server.sinceFirstRequest();

        assertThat(failure).isInstanceOf(sdk.invalidResponse).hasCauseInstanceOf(IOException.class);
        assertThat(server.requestCount()).isEqualTo(1);
        assertThat(elapsed).isBetween(SHORT_CALL.callTimeout().dividedBy(2), SHORT_CALL.callTimeout().plusSeconds(1));
    }

    @ParameterizedTest
    @EnumSource(Sdk.class)
    void errorBodyTricklingPastCallTimeout_retriesOnceWithinWorstCase(Sdk sdk) {
        server.respondInOrder(trickle(503, ERROR_BODY, TRICKLE_INTERVAL, TRICKLE_TOTAL));

        Throwable failure = catchThrowable(() -> sdk.call(server.baseUrl(), SHORT_CALL));
        Duration elapsed = server.sinceFirstRequest();

        assertThat(failure).isInstanceOf(sdk.serverError);
        assertThat(server.requestCount()).isEqualTo(2);
        assertThat(elapsed).isLessThan(worstCase(SHORT_CALL));
    }

    static Stream<Arguments> sdkAndTransientStatus() {
        return Arrays.stream(Sdk.values())
                .flatMap(sdk -> Stream.of(500, 503, 529).map(status -> Arguments.of(sdk, status)));
    }

    static Stream<Arguments> sdkAndLongRetryAfter() {
        String thirtySecondsLater = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30));
        return Arrays.stream(Sdk.values()).flatMap(sdk -> Stream.of(
                Arguments.of(sdk, "Retry-After", "30"),
                Arguments.of(sdk, "Retry-After-Ms", "30000"),
                Arguments.of(sdk, "Retry-After", thirtySecondsLater)));
    }

    static Stream<Arguments> sdkAndShortRetryAfter() {
        return Arrays.stream(Sdk.values()).flatMap(sdk -> Stream.of(
                Arguments.of(sdk, "Retry-After", "1", Duration.ofSeconds(1)),
                Arguments.of(sdk, "Retry-After-Ms", "800", Duration.ofMillis(800))));
    }

    private static Duration worstCase(LlmCallLimits limits) {
        return limits.callTimeout().multipliedBy(limits.maxRetries() + 1L)
                .plus(limits.maxRetryWait().multipliedBy(limits.maxRetries()));
    }

    enum Sdk {
        ANTHROPIC("msg_test", """
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-haiku-4-5",
                 "content": [{"type": "text", "text": "pong"}], "stop_reason": "end_turn", "stop_sequence": null,
                 "usage": {"input_tokens": 1, "output_tokens": 1}}
                """, com.anthropic.errors.InternalServerException.class, AnthropicIoException.class,
                AnthropicInvalidDataException.class) {
            @Override
            String call(String baseUrl, LlmCallLimits limits) {
                AnthropicClient client = LlmClients.withLimits(
                        AnthropicOkHttpClient.builder().baseUrl(baseUrl).apiKey(API_KEY), limits).build();
                try {
                    return client.messages().create(MessageCreateParams.builder()
                            .model("claude-haiku-4-5")
                            .maxTokens(16L)
                            .addUserMessage("ping")
                            .build()).id();
                } finally {
                    client.close();
                }
            }
        },
        OPENAI("chatcmpl-test", """
                {"id": "chatcmpl-test", "object": "chat.completion", "created": 1700000000, "model": "gpt-4o-mini",
                 "choices": [{"index": 0, "message": {"role": "assistant", "content": "pong", "refusal": null},
                              "finish_reason": "stop", "logprobs": null}],
                 "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}
                """, com.openai.errors.InternalServerException.class, OpenAIIoException.class,
                OpenAIInvalidDataException.class) {
            @Override
            String call(String baseUrl, LlmCallLimits limits) {
                OpenAIClient client = LlmClients.withLimits(
                        OpenAIOkHttpClient.builder().baseUrl(baseUrl).apiKey(API_KEY), limits).build();
                try {
                    return client.chat().completions().create(ChatCompletionCreateParams.builder()
                            .model("gpt-4o-mini")
                            .addUserMessage("ping")
                            .build()).id();
                } finally {
                    client.close();
                }
            }
        };

        final String okId;
        final String okBody;
        final Class<? extends Exception> serverError;
        final Class<? extends Exception> ioError;
        final Class<? extends Exception> invalidResponse;

        Sdk(String okId, String okBody, Class<? extends Exception> serverError, Class<? extends Exception> ioError,
            Class<? extends Exception> invalidResponse) {
            this.okId = okId;
            this.okBody = okBody;
            this.serverError = serverError;
            this.ioError = ioError;
            this.invalidResponse = invalidResponse;
        }

        abstract String call(String baseUrl, LlmCallLimits limits);
    }
}
