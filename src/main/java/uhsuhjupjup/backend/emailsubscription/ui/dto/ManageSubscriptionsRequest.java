package uhsuhjupjup.backend.emailsubscription.ui.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ManageSubscriptionsRequest(@NotEmpty List<Long> keywordIds) {
}
