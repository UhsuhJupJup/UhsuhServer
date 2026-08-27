package uhsuhjupjup.backend.pipeline.notification.ui.dto;

import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;

import java.time.LocalDateTime;

public record AdminEmailSendLogResponse(
        Long id,
        String email,
        String recipientType,
        int articleCount,
        String subject,
        LocalDateTime sentAt
) {

    public static AdminEmailSendLogResponse from(NotificationOutbox row) {
        return new AdminEmailSendLogResponse(
                row.getId(),
                row.getRecipient(),
                row.getRecipientType().name(),
                row.getArticleCount(),
                row.getSubject(),
                row.getSentAt());
    }
}
