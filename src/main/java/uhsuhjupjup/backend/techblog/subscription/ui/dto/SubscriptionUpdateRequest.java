package uhsuhjupjup.backend.techblog.subscription.ui.dto;

import java.util.List;

public record SubscriptionUpdateRequest(List<Long> topicIds, List<Long> keywordIds) {
}
