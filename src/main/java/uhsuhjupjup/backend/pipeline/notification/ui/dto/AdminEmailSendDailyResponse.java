package uhsuhjupjup.backend.pipeline.notification.ui.dto;

import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailSendDailySummary;

import java.time.LocalDateTime;

public record AdminEmailSendDailyResponse(
        String date,
        LocalDateTime sentAt,
        long total,
        long memberCount,
        long subscriberCount
) {

    public static AdminEmailSendDailyResponse from(EmailSendDailySummary summary) {
        return new AdminEmailSendDailyResponse(
                summary.date(),
                summary.sentAt(),
                summary.total(),
                summary.memberCount(),
                summary.subscriberCount());
    }
}
