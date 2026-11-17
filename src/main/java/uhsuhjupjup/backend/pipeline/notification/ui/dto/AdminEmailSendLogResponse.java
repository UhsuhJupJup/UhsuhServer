package uhsuhjupjup.backend.pipeline.notification.ui.dto;

import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailSendLog;

import java.time.LocalDateTime;

public record AdminEmailSendLogResponse(
        Long id,
        String email,
        String recipientType,
        int articleCount,
        String subject,
        LocalDateTime sentAt
) {

    public static AdminEmailSendLogResponse from(EmailSendLog log) {
        return new AdminEmailSendLogResponse(
                log.id(),
                log.email(),
                log.recipientType(),
                log.articleCount(),
                log.subject(),
                log.sentAt());
    }
}
