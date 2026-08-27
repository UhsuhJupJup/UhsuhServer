package uhsuhjupjup.backend.pipeline.notification.application.dto;

import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;

import java.util.List;

public record OutboxStatusSummary(
        long pending,
        long sent,
        long failed,
        List<NotificationOutbox> failedEntries
) {
}
