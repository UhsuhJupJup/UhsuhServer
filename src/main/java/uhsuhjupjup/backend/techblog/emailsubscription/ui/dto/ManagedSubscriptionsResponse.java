package uhsuhjupjup.backend.techblog.emailsubscription.ui.dto;

import uhsuhjupjup.backend.techblog.keyword.ui.dto.KeywordResponse;

import java.util.List;

public record ManagedSubscriptionsResponse(String email, List<KeywordResponse> keywords) {
}
