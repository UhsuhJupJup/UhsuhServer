package uhsuhjupjup.backend.config.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

public final class LlmClients {

    private LlmClients() {
    }

    public static AnthropicClient anthropic(LlmCallLimits limits) {
        return withLimits(AnthropicOkHttpClient.builder().fromEnv(), limits).build();
    }

    public static OpenAIClient openAi(LlmCallLimits limits) {
        return withLimits(OpenAIOkHttpClient.builder().fromEnv(), limits).build();
    }

    static AnthropicOkHttpClient.Builder withLimits(AnthropicOkHttpClient.Builder builder, LlmCallLimits limits) {
        return builder
                .timeout(com.anthropic.core.Timeout.builder()
                        .connect(limits.connectTimeout())
                        .read(limits.callTimeout())
                        .request(limits.callTimeout())
                        .build())
                .maxRetries(limits.maxRetries())
                .sleeper(new CappedRetrySleeper(limits.maxRetryWait()));
    }

    static OpenAIOkHttpClient.Builder withLimits(OpenAIOkHttpClient.Builder builder, LlmCallLimits limits) {
        return builder
                .timeout(com.openai.core.Timeout.builder()
                        .connect(limits.connectTimeout())
                        .read(limits.callTimeout())
                        .request(limits.callTimeout())
                        .build())
                .maxRetries(limits.maxRetries())
                .sleeper(new CappedRetrySleeper(limits.maxRetryWait()));
    }
}
