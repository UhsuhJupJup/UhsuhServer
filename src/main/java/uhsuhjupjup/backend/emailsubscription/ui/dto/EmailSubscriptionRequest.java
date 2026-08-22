package uhsuhjupjup.backend.emailsubscription.ui.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record EmailSubscriptionRequest(
        @NotBlank @Email String email,
        @NotEmpty List<Long> keywordIds
) {
}
