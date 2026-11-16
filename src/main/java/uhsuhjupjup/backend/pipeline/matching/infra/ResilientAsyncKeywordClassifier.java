package uhsuhjupjup.backend.pipeline.matching.infra;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.util.List;

@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "claude.enabled", havingValue = "true")
class ResilientAsyncKeywordClassifier implements AsyncKeywordClassifier {

    private static final String CLAUDE_CIRCUIT = "keywordClassifier";
    private static final String GPT_CIRCUIT = "keywordClassifierGpt";

    private final CircuitBreaker claudeCircuit;
    private final CircuitBreaker gptCircuit;
    private final ClaudeWebClientKeywordClassifier claude;
    private final ObjectProvider<GptWebClientKeywordClassifier> gptProvider;

    ResilientAsyncKeywordClassifier(CircuitBreakerRegistry circuitBreakerRegistry,
                                    ClaudeWebClientKeywordClassifier claude,
                                    ObjectProvider<GptWebClientKeywordClassifier> gptProvider) {
        this.claudeCircuit = circuitBreakerRegistry.circuitBreaker(CLAUDE_CIRCUIT);
        this.gptCircuit = circuitBreakerRegistry.circuitBreaker(GPT_CIRCUIT);
        this.claude = claude;
        this.gptProvider = gptProvider;
    }

    @Override
    public Mono<List<KeywordMatch>> classify(String title, String body, MatchCatalog catalog) {
        return Mono.defer(() -> claude.classify(title, body, catalog))
                .transformDeferred(CircuitBreakerOperator.of(claudeCircuit))
                .onErrorResume(claudeError -> {
                    log.warn("Claude 키워드 분류 실패, GPT 폴백 시도 사유={}", claudeError.toString());
                    return classifyWithGpt(title, body, catalog, claudeError);
                });
    }

    private Mono<List<KeywordMatch>> classifyWithGpt(String title, String body, MatchCatalog catalog,
                                                     Throwable claudeError) {
        GptWebClientKeywordClassifier gpt = gptProvider.getIfAvailable();
        if (gpt == null) {
            return Mono.error(new KeywordClassificationException("Claude 분류 실패, GPT 폴백 불가", claudeError));
        }
        return Mono.defer(() -> gpt.classify(title, body, catalog))
                .transformDeferred(CircuitBreakerOperator.of(gptCircuit))
                .onErrorMap(gptError -> {
                    log.warn("GPT 키워드 분류도 실패 사유={}", gptError.toString());
                    return new KeywordClassificationException("Claude, GPT 키워드 분류 모두 실패", gptError);
                });
    }
}
