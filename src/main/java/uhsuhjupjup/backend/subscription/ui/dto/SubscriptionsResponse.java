package uhsuhjupjup.backend.subscription.ui.dto;

import uhsuhjupjup.backend.subscription.application.dto.SubscriptionsResult;

import java.util.List;

public record SubscriptionsResponse(List<Item> topics, List<Item> keywords) {

    public record Item(Long id, String name) {
    }

    public static SubscriptionsResponse from(SubscriptionsResult result) {
        List<Item> topics = result.topics().stream()
                .map(topic -> new Item(topic.getId(), topic.getName()))
                .toList();
        List<Item> keywords = result.keywords().stream()
                .map(keyword -> new Item(keyword.getId(), keyword.getName()))
                .toList();
        return new SubscriptionsResponse(topics, keywords);
    }
}
