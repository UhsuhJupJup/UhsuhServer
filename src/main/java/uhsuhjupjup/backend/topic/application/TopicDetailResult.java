package uhsuhjupjup.backend.topic.application;

import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.topic.domain.Topic;

import java.util.List;

public record TopicDetailResult(Topic topic, List<Keyword> keywords) {
}
