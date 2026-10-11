package uhsuhjupjup.backend.keyword.ui.dto;

import uhsuhjupjup.backend.article.application.dto.KeywordNeighborLink;

public record KeywordNeighborLinkResponse(Long sourceId, Long targetId, long sharedArticleCount) {

    public static KeywordNeighborLinkResponse from(KeywordNeighborLink link) {
        return new KeywordNeighborLinkResponse(
                link.sourceKeywordId(), link.targetKeywordId(), link.sharedArticleCount());
    }
}
