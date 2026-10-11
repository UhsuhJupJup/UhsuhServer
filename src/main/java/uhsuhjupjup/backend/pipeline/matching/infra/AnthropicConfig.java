package uhsuhjupjup.backend.pipeline.matching.infra;

import com.anthropic.client.AnthropicClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uhsuhjupjup.backend.config.llm.LlmCallLimits;
import uhsuhjupjup.backend.config.llm.LlmClients;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "claude.enabled", havingValue = "true")
class AnthropicConfig {

    static final String KEYWORD_CLASSIFIER_CLIENT = "keywordClassifierAnthropicClient";

    @Bean(KEYWORD_CLASSIFIER_CLIENT)
    AnthropicClient keywordClassifierAnthropicClient(
            @Value("${claude.timeout.connect:PT5S}") Duration connectTimeout,
            @Value("${claude.timeout.call:PT10S}") Duration callTimeout,
            @Value("${claude.retry.max-retries:1}") int maxRetries,
            @Value("${claude.retry.max-wait:PT2S}") Duration maxRetryWait) {
        return LlmClients.anthropic(new LlmCallLimits(connectTimeout, callTimeout, maxRetries, maxRetryWait));
    }
}
