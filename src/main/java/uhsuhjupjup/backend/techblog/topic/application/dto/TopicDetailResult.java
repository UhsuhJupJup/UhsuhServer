package uhsuhjupjup.backend.techblog.topic.application.dto;

import uhsuhjupjup.backend.techblog.keyword.domain.Keyword;
import uhsuhjupjup.backend.techblog.topic.domain.Topic;

import java.util.List;

public record TopicDetailResult(Topic topic, List<Keyword> keywords) {
}
