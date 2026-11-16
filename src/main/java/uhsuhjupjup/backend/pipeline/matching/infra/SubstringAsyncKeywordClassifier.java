package uhsuhjupjup.backend.pipeline.matching.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatcher;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.util.List;

@Component
@ConditionalOnProperty(name = "claude.enabled", havingValue = "false", matchIfMissing = true)
class SubstringAsyncKeywordClassifier implements AsyncKeywordClassifier {

    private final KeywordMatcher keywordMatcher;

    SubstringAsyncKeywordClassifier(KeywordMatcher keywordMatcher) {
        this.keywordMatcher = keywordMatcher;
    }

    @Override
    public Mono<List<KeywordMatch>> classify(String title, String body, MatchCatalog catalog) {
        return Mono.fromSupplier(() -> keywordMatcher.match(title, catalog));
    }
}
