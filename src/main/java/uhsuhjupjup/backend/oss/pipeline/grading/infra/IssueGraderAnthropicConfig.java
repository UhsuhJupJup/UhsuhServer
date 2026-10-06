package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.anthropic.client.AnthropicClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uhsuhjupjup.backend.config.llm.LlmCallLimits;
import uhsuhjupjup.backend.config.llm.LlmClients;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "oss.grading.claude.enabled", havingValue = "true")
class IssueGraderAnthropicConfig {

    static final String ISSUE_GRADER_CLIENT = "issueGraderAnthropicClient";

    @Bean(ISSUE_GRADER_CLIENT)
    AnthropicClient issueGraderAnthropicClient(
            @Value("${oss.grading.claude.timeout.connect:PT5S}") Duration connectTimeout,
            @Value("${oss.grading.claude.timeout.call:PT30S}") Duration callTimeout,
            @Value("${oss.grading.claude.retry.max-retries:1}") int maxRetries,
            @Value("${oss.grading.claude.retry.max-wait:PT2S}") Duration maxRetryWait) {
        return LlmClients.anthropic(new LlmCallLimits(connectTimeout, callTimeout, maxRetries, maxRetryWait));
    }
}
