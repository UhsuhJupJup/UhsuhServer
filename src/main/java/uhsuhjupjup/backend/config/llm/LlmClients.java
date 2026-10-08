package uhsuhjupjup.backend.config.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.util.Optional;

public final class LlmClients {

    private static final String ANTHROPIC_API_KEY_PROPERTY = "anthropic.apiKey";
    private static final String ANTHROPIC_API_KEY_ENV = "ANTHROPIC_API_KEY";
    private static final String OPENAI_API_KEY_PROPERTY = "openai.apiKey";
    private static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";

    private LlmClients() {
    }

    public static AnthropicClient anthropic(LlmCallLimits limits) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().fromEnv();
        strippedApiKeyFromEnv(ANTHROPIC_API_KEY_PROPERTY, ANTHROPIC_API_KEY_ENV).ifPresent(builder::apiKey);
        return withLimits(builder, limits).build();
    }

    public static OpenAIClient openAi(LlmCallLimits limits) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder().fromEnv();
        strippedApiKeyFromEnv(OPENAI_API_KEY_PROPERTY, OPENAI_API_KEY_ENV).ifPresent(builder::apiKey);
        return withLimits(builder, limits).build();
    }

    private static Optional<String> strippedApiKeyFromEnv(String systemProperty, String environmentVariable) {
        return Optional.ofNullable(System.getProperty(systemProperty))
                .or(() -> Optional.ofNullable(System.getenv(environmentVariable)))
                .map(String::strip)
                .filter(apiKey -> !apiKey.isEmpty());
    }

    static AnthropicOkHttpClient.Builder withLimits(AnthropicOkHttpClient.Builder builder, LlmCallLimits limits) {
        return builder
                .timeout(com.anthropic.core.Timeout.builder()
                        .connect(limits.connectTimeout())
                        .read(limits.callTimeout())
                        .request(limits.callTimeout())
                        .build())
                .maxRetries(limits.maxRetries())
                .sleeper(new CappedRetrySleeper(limits.maxRetryWait()))
                .logLevel(com.anthropic.core.LogLevel.OFF);
    }

    static OpenAIOkHttpClient.Builder withLimits(OpenAIOkHttpClient.Builder builder, LlmCallLimits limits) {
        return builder
                .timeout(com.openai.core.Timeout.builder()
                        .connect(limits.connectTimeout())
                        .read(limits.callTimeout())
                        .request(limits.callTimeout())
                        .build())
                .maxRetries(limits.maxRetries())
                .sleeper(new CappedRetrySleeper(limits.maxRetryWait()))
                .logLevel(com.openai.core.LogLevel.OFF);
    }
}
