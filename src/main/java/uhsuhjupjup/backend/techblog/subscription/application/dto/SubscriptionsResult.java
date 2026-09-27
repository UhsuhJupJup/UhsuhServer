package uhsuhjupjup.backend.techblog.subscription.application.dto;

import uhsuhjupjup.backend.techblog.keyword.domain.Keyword;
import uhsuhjupjup.backend.techblog.topic.domain.Topic;

import java.util.List;

public record SubscriptionsResult(List<Topic> topics, List<Keyword> keywords) {
}
