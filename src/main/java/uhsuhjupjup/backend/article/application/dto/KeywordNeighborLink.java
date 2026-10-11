package uhsuhjupjup.backend.article.application.dto;

public record KeywordNeighborLink(Long sourceKeywordId, Long targetKeywordId, Long sharedArticleCount) {
}
