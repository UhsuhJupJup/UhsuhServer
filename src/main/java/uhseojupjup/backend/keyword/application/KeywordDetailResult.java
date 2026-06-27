package uhseojupjup.backend.keyword.application;

import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.topic.domain.Topic;

import java.util.List;

public record KeywordDetailResult(Keyword keyword, List<Topic> topics) {
}
