package uhsuhjupjup.backend.techblog.pipeline.notification.application.dto;

import uhsuhjupjup.backend.techblog.pipeline.notification.infra.NotificationOutboxDailyRow;

import java.time.LocalDateTime;

public record EmailSendDailySummary(
        String date,
        LocalDateTime sentAt,
        long total,
        long memberCount,
        long subscriberCount
) {

    public static EmailSendDailySummary from(NotificationOutboxDailyRow row) {
        return new EmailSendDailySummary(
                row.getLogDate(),
                row.getFirstSentAt(),
                row.getTotal(),
                row.getMemberCount(),
                row.getSubscriberCount());
    }
}
