package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final OutboxDispatcher outboxDispatcher;

    @Value("${outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:60000}",
            initialDelayString = "${outbox.poll-initial-delay-ms:60000}")
    @SchedulerLock(name = "notificationOutboxDispatch", lockAtMostFor = "${outbox.lock-at-most-for:PT5M}")
    public void dispatch() {
        List<NotificationOutbox> due = notificationOutboxRepository.findDue(
                OutboxStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, batchSize));
        if (due.isEmpty()) {
            return;
        }
        int sent = 0;
        int failed = 0;
        for (NotificationOutbox row : due) {
            if (outboxDispatcher.dispatchOne(row.getId())) {
                sent++;
            } else {
                failed++;
            }
        }
        log.info("아웃박스 처리 대상={} 발송={} 실패={}", due.size(), sent, failed);
    }
}
