package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.pipeline.notification.application.dto.OutboxStatusSummary;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxAdminService {

    private final NotificationOutboxRepository notificationOutboxRepository;

    @Transactional(readOnly = true)
    public OutboxStatusSummary summary(int failedLimit) {
        long pending = notificationOutboxRepository.countByStatus(OutboxStatus.PENDING);
        long sent = notificationOutboxRepository.countByStatus(OutboxStatus.SENT);
        long failed = notificationOutboxRepository.countByStatus(OutboxStatus.FAILED);
        List<NotificationOutbox> failedEntries = notificationOutboxRepository.findByStatusOrderByCreatedAtDesc(
                OutboxStatus.FAILED, PageRequest.of(0, failedLimit));
        return new OutboxStatusSummary(pending, sent, failed, failedEntries);
    }

    @Transactional(readOnly = true)
    public List<NotificationOutbox> recentSent(int limit) {
        return notificationOutboxRepository.findByStatusOrderBySentAtDesc(
                OutboxStatus.SENT, PageRequest.of(0, limit));
    }

    @Transactional
    public void requeue(Long id) {
        NotificationOutbox row = notificationOutboxRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        row.requeue(LocalDateTime.now());
    }
}
