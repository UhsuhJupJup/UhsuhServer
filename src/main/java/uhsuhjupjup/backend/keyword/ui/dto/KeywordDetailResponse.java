package uhsuhjupjup.backend.keyword.ui.dto;

import uhsuhjupjup.backend.keyword.application.dto.KeywordDetailResult;
import uhsuhjupjup.backend.topic.ui.dto.TopicResponse;

import java.util.List;

public record KeywordDetailResponse(
        Long id,
        String name,
        List<TopicResponse> topics,
        long articleCount,
        List<RelatedKeywordResponse> relatedKeywords) {

    public static KeywordDetailResponse from(KeywordDetailResult result) {
        return new KeywordDetailResponse(
                result.keyword().getId(),
                result.keyword().getName(),
                result.topics().stream().map(TopicResponse::from).toList(),
                result.summary().articleCount(),
                result.summary().relatedKeywords().stream().map(RelatedKeywordResponse::from).toList()
        );
    }
}
