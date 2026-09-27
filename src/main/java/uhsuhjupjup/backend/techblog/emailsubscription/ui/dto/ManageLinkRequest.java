package uhsuhjupjup.backend.techblog.emailsubscription.ui.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ManageLinkRequest(@NotBlank @Email String email) {
}
