package uhseojupjup.backend.topic.ui.dto;

import uhseojupjup.backend.topic.domain.Topic;

public record TopicResponse(Long id, String name) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getName());
    }
}
