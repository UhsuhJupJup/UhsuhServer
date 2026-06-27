package uhsuhjupjup.backend.subscription.application.dto;

import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.topic.domain.Topic;

import java.util.List;

public record SubscriptionsResult(List<Topic> topics, List<Keyword> keywords) {
}
