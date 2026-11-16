package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxRecoverer {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final OutboxClaimer outboxClaimer;

    @Value("${outbox.republish-delay:PT10M}")
    private Duration republishDelay;

    @Transactional
    public List<Long> collect(int limit, LocalDateTime now) {
        List<Long> ids = new ArrayList<>(notificationOutboxRepository.lockRepublishablePendingIds(now, limit));
        if (ids.size() < limit) {
            ids.addAll(outboxClaimer.isolateExhausted(
                    notificationOutboxRepository.lockExpiredProcessingIds(now, limit - ids.size())));
        }
        if (!ids.isEmpty()) {
            notificationOutboxRepository.deferRepublishAll(ids, now.plus(republishDelay));
        }
        return ids;
    }
}
