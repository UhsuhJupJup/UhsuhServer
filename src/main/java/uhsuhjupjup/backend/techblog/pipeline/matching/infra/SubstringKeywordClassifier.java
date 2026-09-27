package uhsuhjupjup.backend.techblog.pipeline.matching.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.techblog.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.techblog.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.techblog.pipeline.matching.domain.KeywordMatcher;
import uhsuhjupjup.backend.techblog.pipeline.matching.domain.MatchCatalog;

import java.util.List;

@Component
@ConditionalOnProperty(name = "claude.enabled", havingValue = "false", matchIfMissing = true)
class SubstringKeywordClassifier implements KeywordClassifier {

    private final KeywordMatcher keywordMatcher;

    SubstringKeywordClassifier(KeywordMatcher keywordMatcher) {
        this.keywordMatcher = keywordMatcher;
    }

    @Override
    public List<KeywordMatch> classify(String title, String body, MatchCatalog catalog) {
        return keywordMatcher.match(title, catalog);
    }
}
