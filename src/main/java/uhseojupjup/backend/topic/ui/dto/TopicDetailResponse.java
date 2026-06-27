package uhseojupjup.backend.topic.ui.dto;

import uhseojupjup.backend.keyword.ui.dto.KeywordResponse;
import uhseojupjup.backend.topic.application.TopicDetailResult;

import java.util.List;

public record TopicDetailResponse(Long id, String name, List<KeywordResponse> keywords) {

    public static TopicDetailResponse from(TopicDetailResult result) {
        return new TopicDetailResponse(
                result.topic().getId(),
                result.topic().getName(),
                result.keywords().stream().map(KeywordResponse::from).toList()
        );
    }
}
