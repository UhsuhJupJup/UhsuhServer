package uhsuhjupjup.backend.matching.domain;

import java.util.List;

public interface KeywordMatcher {

    List<KeywordMatch> match(String title, MatchCatalog catalog);
}
