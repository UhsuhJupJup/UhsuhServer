package uhseojupjup.backend.keyword.ui.dto;

import uhseojupjup.backend.keyword.application.KeywordDetailResult;
import uhseojupjup.backend.topic.ui.dto.TopicResponse;

import java.util.List;

public record KeywordDetailResponse(Long id, String name, List<TopicResponse> topics) {

    public static KeywordDetailResponse from(KeywordDetailResult result) {
        return new KeywordDetailResponse(
                result.keyword().getId(),
                result.keyword().getName(),
                result.topics().stream().map(TopicResponse::from).toList()
        );
    }
}
