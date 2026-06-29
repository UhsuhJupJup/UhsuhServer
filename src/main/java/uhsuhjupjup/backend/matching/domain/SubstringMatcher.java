package uhsuhjupjup.backend.matching.domain;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
class SubstringMatcher implements KeywordMatcher {

    @Override
    public List<KeywordMatch> match(String title, MatchCatalog catalog) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        String lowerTitle = title.toLowerCase(Locale.ROOT);
        List<KeywordMatch> matches = new ArrayList<>();
        for (MatchTarget target : catalog.targets()) {
            if (lowerTitle.contains(target.lowerName())) {
                matches.add(new KeywordMatch(target.keywordId(), "title"));
            } else if (containsAny(lowerTitle, target.lowerAliases())) {
                matches.add(new KeywordMatch(target.keywordId(), "alias"));
            }
        }
        return matches;
    }

    private boolean containsAny(String lowerTitle, Set<String> lowerAliases) {
        for (String alias : lowerAliases) {
            if (!alias.isBlank() && lowerTitle.contains(alias)) {
                return true;
            }
        }
        return false;
    }
}
