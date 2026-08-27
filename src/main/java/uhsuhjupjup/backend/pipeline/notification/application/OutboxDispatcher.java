package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final EmailSender emailSender;

    @Value("${outbox.max-attempts:15}")
    private int maxAttempts;

    @Value("${outbox.retry-backoff-base:PT5M}")
    private Duration retryBackoffBase;

    @Value("${outbox.retry-backoff-cap:PT30M}")
    private Duration retryBackoffCap;

    @Transactional
    public boolean dispatchOne(Long id) {
        NotificationOutbox row = notificationOutboxRepository.findById(id).orElse(null);
        if (row == null || row.getStatus() != OutboxStatus.PENDING) {
            return false;
        }
        try {
            emailSender.send(new EmailMessage(row.getRecipient(), row.getSubject(), row.getBody(),
                    row.getUnsubscribeUrl()));
            row.markSent(LocalDateTime.now());
            return true;
        } catch (EmailSendException e) {
            handleFailure(row, e.getMessage(), e.isPermanent());
            return false;
        } catch (Exception e) {
            handleFailure(row, e.getMessage(), false);
            return false;
        }
    }

    private void handleFailure(NotificationOutbox row, String error, boolean permanent) {
        if (permanent) {
            row.markFailed(error);
            log.warn("아웃박스 발송 영구 실패 즉시 격리 id={} 사유={}", row.getId(), error);
            return;
        }
        if (row.getAttempts() + 1 >= maxAttempts) {
            row.markFailed(error);
            log.warn("아웃박스 발송 재시도 지평 초과 격리 id={} attempts={} 사유={}",
                    row.getId(), row.getAttempts(), error);
            return;
        }
        LocalDateTime nextAttemptAt = LocalDateTime.now().plus(backoff(row.getAttempts()));
        row.markRetry(error, nextAttemptAt);
        log.warn("아웃박스 발송 실패 재시도예약 id={} attempts={} next={} 사유={}",
                row.getId(), row.getAttempts(), nextAttemptAt, error);
    }

    private Duration backoff(int attempts) {
        long factor = 1L << Math.min(attempts, 16);
        Duration backoff = retryBackoffBase.multipliedBy(factor);
        return backoff.compareTo(retryBackoffCap) > 0 ? retryBackoffCap : backoff;
    }
}
