package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxClaimer {

    private static final String LEASE_EXHAUSTED_ERROR = "선점 만료가 반복되어 재시도 한도를 넘음";

    private final NotificationOutboxRepository notificationOutboxRepository;

    @Value("${outbox.max-attempts:15}")
    private int maxAttempts;

    @Value("${outbox.claim-lease:PT1H}")
    private Duration claimLease;

    @Value("${outbox.message-claim-lease:PT10M}")
    private Duration messageClaimLease;

    @Transactional
    public List<Long> claim(int limit, LocalDateTime now) {
        List<Long> claimed = new ArrayList<>(
                notificationOutboxRepository.lockDueIds(OutboxStatus.PENDING.name(), now, limit));
        if (claimed.size() < limit) {
            claimed.addAll(reclaimExpired(limit - claimed.size(), now));
        }
        if (!claimed.isEmpty()) {
            notificationOutboxRepository.claimAll(claimed, OutboxStatus.PROCESSING, now.plus(claimLease));
        }
        return claimed;
    }

    @Transactional
    public boolean claimOne(Long id, LocalDateTime now) {
        return notificationOutboxRepository.claimOne(id, OutboxStatus.PENDING, OutboxStatus.PROCESSING, now,
                now.plus(messageClaimLease), maxAttempts) == 1;
    }

    @Transactional
    public int release(List<Long> ids, LocalDateTime now) {
        if (ids.isEmpty()) {
            return 0;
        }
        return notificationOutboxRepository.releaseAll(ids, OutboxStatus.PROCESSING, OutboxStatus.PENDING, now);
    }

    @Transactional
    public List<Long> isolateExhausted(List<Long> expired) {
        List<Long> remaining = new ArrayList<>(expired);
        if (remaining.isEmpty()) {
            return remaining;
        }
        List<Long> exhausted = notificationOutboxRepository.findIdsReachedAttempts(remaining, maxAttempts);
        if (!exhausted.isEmpty()) {
            notificationOutboxRepository.failAll(exhausted, OutboxStatus.FAILED, LEASE_EXHAUSTED_ERROR);
            remaining.removeAll(exhausted);
            log.warn("아웃박스 선점 만료 반복 격리 ids={}", exhausted);
        }
        return remaining;
    }

    private List<Long> reclaimExpired(int limit, LocalDateTime now) {
        List<Long> expired = isolateExhausted(
                notificationOutboxRepository.lockDueIds(OutboxStatus.PROCESSING.name(), now, limit));
        if (!expired.isEmpty()) {
            log.warn("아웃박스 선점 만료 작업 다시 선점 ids={}", expired);
        }
        return expired;
    }
}
