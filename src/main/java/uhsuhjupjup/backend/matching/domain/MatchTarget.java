package uhsuhjupjup.backend.matching.domain;

import java.util.Set;

public record MatchTarget(Long keywordId, String lowerName, Set<String> lowerAliases) {
}
