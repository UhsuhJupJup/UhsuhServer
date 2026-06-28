package uhsuhjupjup.backend.collection.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
class FeedHttpConfig {

    @Bean
    RestClient feedRestClient(RestClient.Builder builder) {
        return builder
                .defaultHeader(HttpHeaders.USER_AGENT, "UhseoJupJup/1.0 (+https://github.com/sinequanonn/UhseoJupJup)")
                .build();
    }
}
