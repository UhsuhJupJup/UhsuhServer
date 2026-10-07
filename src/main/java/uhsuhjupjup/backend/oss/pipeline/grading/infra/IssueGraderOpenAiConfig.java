package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.openai.client.OpenAIClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uhsuhjupjup.backend.config.llm.LlmCallLimits;
import uhsuhjupjup.backend.config.llm.LlmClients;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "oss.grading.gpt.enabled", havingValue = "true")
class IssueGraderOpenAiConfig {

    static final String ISSUE_GRADER_CLIENT = "issueGraderOpenAiClient";

    @Bean(ISSUE_GRADER_CLIENT)
    OpenAIClient issueGraderOpenAiClient(
            @Value("${oss.grading.gpt.timeout.connect:PT5S}") Duration connectTimeout,
            @Value("${oss.grading.gpt.timeout.call:PT30S}") Duration callTimeout,
            @Value("${oss.grading.gpt.retry.max-retries:1}") int maxRetries,
            @Value("${oss.grading.gpt.retry.max-wait:PT2S}") Duration maxRetryWait) {
        return LlmClients.openAi(new LlmCallLimits(connectTimeout, callTimeout, maxRetries, maxRetryWait));
    }
}
