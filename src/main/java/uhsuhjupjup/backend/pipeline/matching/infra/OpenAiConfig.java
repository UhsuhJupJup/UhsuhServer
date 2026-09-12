package uhsuhjupjup.backend.pipeline.matching.infra;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "gpt.enabled", havingValue = "true")
class OpenAiConfig {

    @Bean
    OpenAIClient openAiClient(
            @Value("${gpt.timeout.connect:PT5S}") Duration connectTimeout,
            @Value("${gpt.timeout.call:PT10S}") Duration callTimeout) {
        return OpenAIOkHttpClient.builder()
                .fromEnv()
                .timeout(Timeout.builder()
                        .connect(connectTimeout)
                        .read(callTimeout)
                        .request(callTimeout)
                        .build())
                .build();
    }
}
