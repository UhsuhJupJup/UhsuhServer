package uhsuhjupjup.backend.pipeline.notification.ui.dto;

import uhsuhjupjup.backend.pipeline.notification.application.dto.OutboxStatusSummary;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;

import java.time.LocalDateTime;
import java.util.List;

public record AdminOutboxResponse(
        long pending,
        long sent,
        long failed,
        List<Entry> failedEntries
) {

    public static AdminOutboxResponse from(OutboxStatusSummary summary) {
        List<Entry> entries = summary.failedEntries().stream()
                .map(Entry::from)
                .toList();
        return new AdminOutboxResponse(summary.pending(), summary.sent(), summary.failed(), entries);
    }

    public record Entry(
            Long id,
            String recipient,
            String recipientType,
            String subject,
            int attempts,
            String lastError,
            LocalDateTime createdAt
    ) {

        public static Entry from(NotificationOutbox row) {
            return new Entry(
                    row.getId(),
                    row.getRecipient(),
                    row.getRecipientType().name(),
                    row.getSubject(),
                    row.getAttempts(),
                    row.getLastError(),
                    row.getCreatedAt());
        }
    }
}
