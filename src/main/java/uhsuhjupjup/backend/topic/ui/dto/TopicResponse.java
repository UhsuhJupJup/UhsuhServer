package uhsuhjupjup.backend.topic.ui.dto;

import uhsuhjupjup.backend.topic.domain.Topic;

public record TopicResponse(Long id, String name) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getName());
    }
}
