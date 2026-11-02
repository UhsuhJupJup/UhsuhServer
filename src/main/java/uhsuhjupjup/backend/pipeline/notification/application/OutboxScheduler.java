package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxClaimer outboxClaimer;
    private final OutboxDispatcher outboxDispatcher;

    @Value("${outbox.batch-size:5000}")
    private int batchSize;

    private volatile boolean stopping;

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:60000}",
            initialDelayString = "${outbox.poll-initial-delay-ms:60000}")
    public void dispatch() {
        if (stopping) {
            return;
        }
        List<Long> claimed = outboxClaimer.claim(batchSize, LocalDateTime.now());
        if (claimed.isEmpty()) {
            return;
        }
        int sent = 0;
        int failed = 0;
        int released = 0;
        for (int i = 0; i < claimed.size(); i++) {
            if (stopping) {
                released = outboxClaimer.release(claimed.subList(i, claimed.size()), LocalDateTime.now());
                break;
            }
            if (outboxDispatcher.dispatchClaimed(claimed.get(i))) {
                sent++;
            } else {
                failed++;
            }
        }
        log.info("아웃박스 처리 선점={} 발송={} 실패={} 반납={}", claimed.size(), sent, failed, released);
    }

    @EventListener(ContextClosedEvent.class)
    public void stop() {
        stopping = true;
    }
}
