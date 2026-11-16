package uhsuhjupjup.backend.config.llm;

import io.netty.channel.ChannelOption;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public final class LlmWebClients {

    private static final Duration FIRST_RETRY_BACKOFF = Duration.ofMillis(500);
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private LlmWebClients() {
    }

    public static WebClient create(String baseUrl, LlmCallLimits limits, Map<String, String> headers) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(limits.connectTimeout().toMillis()))
                .responseTimeout(limits.callTimeout());
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    headers.forEach(httpHeaders::set);
                })
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }

    public static <T> Mono<T> withLimits(Mono<T> call, LlmCallLimits limits) {
        Mono<T> attempt = call.timeout(limits.callTimeout());
        if (limits.maxRetries() == 0) {
            return attempt;
        }
        return attempt.retryWhen(Retry.backoff(limits.maxRetries(), FIRST_RETRY_BACKOFF)
                .maxBackoff(limits.maxRetryWait())
                .filter(LlmWebClients::isRetryable)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    static boolean isRetryable(Throwable error) {
        if (error instanceof WebClientRequestException || error instanceof TimeoutException) {
            return true;
        }
        if (error instanceof WebClientResponseException response) {
            HttpStatusCode status = response.getStatusCode();
            return status.is5xxServerError() || status.value() == 408 || status.value() == 409
                    || status.value() == 429;
        }
        return false;
    }
}
