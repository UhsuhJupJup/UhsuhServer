package uhsuhjupjup.backend.emailsubscription.ui.dto;

import uhsuhjupjup.backend.keyword.ui.dto.KeywordResponse;

import java.util.List;

public record ManagedSubscriptionsResponse(String email, List<KeywordResponse> keywords) {
}
