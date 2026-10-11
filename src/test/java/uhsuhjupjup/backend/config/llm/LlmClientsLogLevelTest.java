package uhsuhjupjup.backend.config.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.status;

@ExtendWith(OutputCaptureExtension.class)
class LlmClientsLogLevelTest {

    private static final String API_KEY = "test-key";
    private static final String PROMPT_MARKER = "PROMPTMARKER";
    private static final String ANSWER_MARKER = "ANSWERMARKER";
    private static final LlmCallLimits LIMITS =
            new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(5), 1, Duration.ofSeconds(2));
    private static final String ANTHROPIC_ANSWER = """
            {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-haiku-4-5",
             "content": [{"type": "text", "text": "ANSWERMARKER"}], "stop_reason": "end_turn", "stop_sequence": null,
             "usage": {"input_tokens": 1, "output_tokens": 1}}
            """;
    private static final String OPENAI_ANSWER = """
            {"id": "chatcmpl-test", "object": "chat.completion", "created": 1700000000, "model": "gpt-4o-mini",
             "choices": [{"index": 0, "message": {"role": "assistant", "content": "ANSWERMARKER", "refusal": null},
                          "finish_reason": "stop", "logprobs": null}],
             "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}
            """;

    private MockLlmServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockLlmServer();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void anthropic_atDebugLevelWithoutTheLimits_printsTheBodies(CapturedOutput output) {
        server.respondInOrder(status(200, ANTHROPIC_ANSWER));

        askAnthropic(anthropicAtDebugLevel().build());

        assertThat(output).contains(PROMPT_MARKER, ANSWER_MARKER);
    }

    @Test
    void anthropic_withLimits_keepsTheSdkLogOffEvenAtDebugLevel(CapturedOutput output) {
        server.respondInOrder(status(200, ANTHROPIC_ANSWER));

        askAnthropic(LlmClients.withLimits(anthropicAtDebugLevel(), LIMITS).build());

        assertThat(output).doesNotContain(PROMPT_MARKER).doesNotContain(ANSWER_MARKER).doesNotContain("-->");
    }

    @Test
    void openAi_atDebugLevelWithoutTheLimits_printsTheBodies(CapturedOutput output) {
        server.respondInOrder(status(200, OPENAI_ANSWER));

        askOpenAi(openAiAtDebugLevel().build());

        assertThat(output).contains(PROMPT_MARKER, ANSWER_MARKER);
    }

    @Test
    void openAi_withLimits_keepsTheSdkLogOffEvenAtDebugLevel(CapturedOutput output) {
        server.respondInOrder(status(200, OPENAI_ANSWER));

        askOpenAi(LlmClients.withLimits(openAiAtDebugLevel(), LIMITS).build());

        assertThat(output).doesNotContain(PROMPT_MARKER).doesNotContain(ANSWER_MARKER).doesNotContain("-->");
    }

    private AnthropicOkHttpClient.Builder anthropicAtDebugLevel() {
        return AnthropicOkHttpClient.builder()
                .baseUrl(server.baseUrl())
                .apiKey(API_KEY)
                .logLevel(com.anthropic.core.LogLevel.DEBUG);
    }

    private OpenAIOkHttpClient.Builder openAiAtDebugLevel() {
        return OpenAIOkHttpClient.builder()
                .baseUrl(server.baseUrl())
                .apiKey(API_KEY)
                .logLevel(com.openai.core.LogLevel.DEBUG);
    }

    private static void askAnthropic(AnthropicClient client) {
        try {
            client.messages().create(MessageCreateParams.builder()
                    .model("claude-haiku-4-5")
                    .maxTokens(16L)
                    .addUserMessage(PROMPT_MARKER)
                    .build());
        } finally {
            client.close();
        }
    }

    private static void askOpenAi(OpenAIClient client) {
        try {
            client.chat().completions().create(ChatCompletionCreateParams.builder()
                    .model("gpt-4o-mini")
                    .addUserMessage(PROMPT_MARKER)
                    .build());
        } finally {
            client.close();
        }
    }
}
