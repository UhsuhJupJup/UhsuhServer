package uhsuhjupjup.backend.pipeline.matching.infra;

import com.openai.client.OpenAIClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uhsuhjupjup.backend.config.llm.LlmCallLimits;
import uhsuhjupjup.backend.config.llm.LlmClients;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "gpt.enabled", havingValue = "true")
class OpenAiConfig {

    static final String KEYWORD_CLASSIFIER_CLIENT = "keywordClassifierOpenAiClient";

    @Bean(KEYWORD_CLASSIFIER_CLIENT)
    OpenAIClient keywordClassifierOpenAiClient(
            @Value("${gpt.timeout.connect:PT5S}") Duration connectTimeout,
            @Value("${gpt.timeout.call:PT10S}") Duration callTimeout,
            @Value("${gpt.retry.max-retries:1}") int maxRetries,
            @Value("${gpt.retry.max-wait:PT2S}") Duration maxRetryWait) {
        return LlmClients.openAi(new LlmCallLimits(connectTimeout, callTimeout, maxRetries, maxRetryWait));
    }
}
