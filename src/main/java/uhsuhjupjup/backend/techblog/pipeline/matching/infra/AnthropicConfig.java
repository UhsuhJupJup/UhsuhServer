package uhsuhjupjup.backend.techblog.pipeline.matching.infra;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "claude.enabled", havingValue = "true")
class AnthropicConfig {

    @Bean
    AnthropicClient anthropicClient(
            @Value("${claude.timeout.connect:PT5S}") Duration connectTimeout,
            @Value("${claude.timeout.call:PT10S}") Duration callTimeout) {
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(Timeout.builder()
                        .connect(connectTimeout)
                        .read(callTimeout)
                        .request(callTimeout)
                        .build())
                .build();
    }
}
