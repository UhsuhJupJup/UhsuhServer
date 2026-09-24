package uhsuhjupjup.backend.pipeline.matching.infra;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.util.List;

@Slf4j
@Primary
@Component("keywordClassifier")
@ConditionalOnProperty(name = "claude.enabled", havingValue = "true")
class ResilientKeywordClassifier implements KeywordClassifier {

    private static final String CLAUDE_CIRCUIT = "keywordClassifier";
    private static final String GPT_CIRCUIT = "keywordClassifierGpt";

    private final CircuitBreaker claudeCircuit;
    private final CircuitBreaker gptCircuit;
    private final ClaudeKeywordClassifier claude;
    private final ObjectProvider<GptKeywordClassifier> gptProvider;

    ResilientKeywordClassifier(CircuitBreakerRegistry circuitBreakerRegistry,
                               ClaudeKeywordClassifier claude,
                               ObjectProvider<GptKeywordClassifier> gptProvider) {
        this.claudeCircuit = circuitBreakerRegistry.circuitBreaker(CLAUDE_CIRCUIT);
        this.gptCircuit = circuitBreakerRegistry.circuitBreaker(GPT_CIRCUIT);
        this.claude = claude;
        this.gptProvider = gptProvider;
    }

    @Override
    public List<KeywordMatch> classify(String title, String body, MatchCatalog catalog) {
        try {
            return claudeCircuit.executeCallable(() -> claude.classify(title, body, catalog));
        } catch (Exception claudeError) {
            log.warn("Claude 키워드 분류 실패, GPT 폴백 시도 사유={}", claudeError.toString());
            return classifyWithGpt(title, body, catalog, claudeError);
        }
    }

    private List<KeywordMatch> classifyWithGpt(String title, String body, MatchCatalog catalog, Exception claudeError) {
        GptKeywordClassifier gpt = gptProvider.getIfAvailable();
        if (gpt == null) {
            log.warn("GPT 분류기 비활성, 폴백 불가");
            throw new KeywordClassificationException("Claude 분류 실패, GPT 폴백 불가", claudeError);
        }
        try {
            return gptCircuit.executeCallable(() -> gpt.classify(title, body, catalog));
        } catch (Exception gptError) {
            log.warn("GPT 키워드 분류도 실패 사유={}", gptError.toString());
            throw new KeywordClassificationException("Claude, GPT 키워드 분류 모두 실패", gptError);
        }
    }
}
