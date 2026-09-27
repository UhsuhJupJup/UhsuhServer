package uhsuhjupjup.backend.keyword.application.dto;

import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.techblog.topic.domain.Topic;

import java.util.List;

public record KeywordDetailResult(Keyword keyword, List<Topic> topics) {
}
