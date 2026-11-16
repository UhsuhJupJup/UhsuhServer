package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private final ObjectProvider<OutboxMessageQueue> outboxMessageQueue;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(OutboxCreatedEvent event) {
        OutboxMessageQueue queue = outboxMessageQueue.getIfAvailable();
        if (queue == null) {
            return;
        }
        try {
            queue.publish(event.outboxId());
        } catch (RuntimeException e) {
            log.warn("아웃박스 발송 메시지 발행 실패, 폴러가 이어서 처리 id={} 사유={}", event.outboxId(), e.getMessage());
        }
    }
}
