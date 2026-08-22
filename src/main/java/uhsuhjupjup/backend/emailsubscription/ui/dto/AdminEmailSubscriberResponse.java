package uhsuhjupjup.backend.emailsubscription.ui.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminEmailSubscriberResponse(
        Long id,
        String email,
        boolean verified,
        List<String> keywords,
        LocalDateTime createdAt
) {
}
