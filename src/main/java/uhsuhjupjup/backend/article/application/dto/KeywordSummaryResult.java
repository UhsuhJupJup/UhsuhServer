package uhsuhjupjup.backend.article.application.dto;

import java.util.List;

public record KeywordSummaryResult(long articleCount, List<KeywordRelation> relatedKeywords) {
}
