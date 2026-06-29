package uhsuhjupjup.backend.matching.domain;

import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.domain.KeywordAlias;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record MatchCatalog(List<MatchTarget> targets) {

    public static MatchCatalog from(List<Keyword> keywords, List<KeywordAlias> aliases) {
        Map<Long, Set<String>> aliasesByKeyword = aliases.stream()
                .collect(Collectors.groupingBy(
                        KeywordAlias::getKeywordId,
                        Collectors.mapping(alias -> alias.getAlias().toLowerCase(Locale.ROOT), Collectors.toSet())));
        List<MatchTarget> targets = keywords.stream()
                .map(keyword -> new MatchTarget(
                        keyword.getId(),
                        keyword.getName().toLowerCase(Locale.ROOT),
                        aliasesByKeyword.getOrDefault(keyword.getId(), Set.of())))
                .toList();
        return new MatchCatalog(targets);
    }
}
