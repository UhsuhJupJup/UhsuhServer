package uhsuhjupjup.backend.pipeline.matching.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class KeywordResolver {

    private KeywordResolver() {
    }

    public static List<KeywordMatch> resolve(List<String> candidateNames, MatchCatalog catalog, String matchedVia) {
        if (candidateNames == null || candidateNames.isEmpty() || catalog.targets().isEmpty()) {
            return List.of();
        }
        Map<String, Long> keywordIdByName = keywordIdByName(catalog);
        Set<Long> seen = new LinkedHashSet<>();
        List<KeywordMatch> matches = new ArrayList<>();
        for (String raw : candidateNames) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Long keywordId = keywordIdByName.get(raw.strip().toLowerCase(Locale.ROOT));
            if (keywordId != null && seen.add(keywordId)) {
                matches.add(new KeywordMatch(keywordId, matchedVia));
            }
        }
        return matches;
    }

    private static Map<String, Long> keywordIdByName(MatchCatalog catalog) {
        Map<String, Long> keywordIdByName = new LinkedHashMap<>();
        for (MatchTarget target : catalog.targets()) {
            keywordIdByName.put(target.lowerName(), target.keywordId());
            for (String alias : target.lowerAliases()) {
                keywordIdByName.putIfAbsent(alias, target.keywordId());
            }
        }
        return keywordIdByName;
    }
}
