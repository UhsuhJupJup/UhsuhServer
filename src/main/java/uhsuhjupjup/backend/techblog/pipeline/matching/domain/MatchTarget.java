package uhsuhjupjup.backend.techblog.pipeline.matching.domain;

import java.util.Set;

public record MatchTarget(Long keywordId, String lowerName, Set<String> lowerAliases) {
}
