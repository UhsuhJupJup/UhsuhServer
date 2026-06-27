package uhseojupjup.backend.topic.application;

import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.topic.domain.Topic;

import java.util.List;

public record TopicDetailResult(Topic topic, List<Keyword> keywords) {
}
