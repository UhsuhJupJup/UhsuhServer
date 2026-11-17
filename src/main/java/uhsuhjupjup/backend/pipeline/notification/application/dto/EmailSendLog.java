package uhsuhjupjup.backend.pipeline.notification.application.dto;

import uhsuhjupjup.backend.pipeline.notification.infra.NotificationSentLogRow;

import java.time.LocalDateTime;

public record EmailSendLog(
        Long id,
        String email,
        String recipientType,
        int articleCount,
        String subject,
        LocalDateTime sentAt
) {

    public static EmailSendLog from(NotificationSentLogRow row) {
        return new EmailSendLog(
                row.getId(),
                row.getRecipient(),
                row.getRecipientType(),
                row.getArticleCount(),
                row.getSubject(),
                row.getSentAt());
    }
}
