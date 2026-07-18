package uhsuhjupjup.backend.pipeline.matching.application;

import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.util.List;

public interface KeywordClassifier {

    List<KeywordMatch> classify(String title, String body, MatchCatalog catalog);
}
