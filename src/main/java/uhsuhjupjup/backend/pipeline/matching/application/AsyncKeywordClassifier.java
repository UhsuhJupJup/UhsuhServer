package uhsuhjupjup.backend.pipeline.matching.application;

import reactor.core.publisher.Mono;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.util.List;

public interface AsyncKeywordClassifier {

    Mono<List<KeywordMatch>> classify(String title, String body, MatchCatalog catalog);
}
