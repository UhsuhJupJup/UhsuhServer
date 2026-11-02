package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxMessageHandler {

    private final OutboxClaimer outboxClaimer;
    private final OutboxDispatcher outboxDispatcher;
    private final NotificationOutboxRepository notificationOutboxRepository;

    public OutboxMessageOutcome handle(Long outboxId) {
        LocalDateTime now = LocalDateTime.now();
        if (outboxClaimer.claimOne(outboxId, now)) {
            outboxDispatcher.dispatchClaimed(outboxId);
            return OutboxMessageOutcome.DONE;
        }
        NotificationOutbox row = notificationOutboxRepository.findById(outboxId).orElse(null);
        if (row != null && row.isProcessing() && row.getNextAttemptAt().isAfter(now)) {
            return OutboxMessageOutcome.KEEP;
        }
        return OutboxMessageOutcome.DONE;
    }
}
