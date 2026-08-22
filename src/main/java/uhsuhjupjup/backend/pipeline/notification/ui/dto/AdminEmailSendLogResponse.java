package uhsuhjupjup.backend.pipeline.notification.ui.dto;

import uhsuhjupjup.backend.pipeline.notification.domain.EmailSendLog;

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
                log.getId(),
                log.getEmail(),
                log.getRecipientType().name(),
                log.getArticleCount(),
                log.getSubject(),
                log.getSentAt());
    }
}
