package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "outbox.stream.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRecoveryPoller {

    private final OutboxRecoverer outboxRecoverer;
    private final OutboxMessageQueue outboxMessageQueue;

    @Value("${outbox.batch-size:5000}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:60000}",
            initialDelayString = "${outbox.poll-initial-delay-ms:60000}")
    @SchedulerLock(name = "notificationOutboxRecovery", lockAtMostFor = "${outbox.recovery-lock-at-most-for:PT5M}")
    public void recover() {
        List<Long> ids = outboxRecoverer.collect(batchSize, LocalDateTime.now());
        if (ids.isEmpty()) {
            return;
        }
        int published = 0;
        for (Long id : ids) {
            try {
                outboxMessageQueue.publish(id);
                published++;
            } catch (RuntimeException e) {
                log.warn("아웃박스 복구 발행 중단, 다음 복구 시각에 다시 시도 id={} 사유={}", id, e.getMessage());
                break;
            }
        }
        log.info("아웃박스 복구 대상={} 다시 발행={}", ids.size(), published);
    }
}
