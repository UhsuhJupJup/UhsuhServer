package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
class OutboxSentArchiver {

    private final NotificationOutboxRepository notificationOutboxRepository;

    @Transactional
    public int archiveChunk(LocalDateTime sentBefore, int limit, LocalDateTime archivedAt) {
        List<Long> ids = notificationOutboxRepository.findSentIdsBefore(sentBefore, limit);
        if (ids.isEmpty()) {
            return 0;
        }
        int copied = notificationOutboxRepository.copySentToArchive(ids, archivedAt);
        int deleted = notificationOutboxRepository.deleteSentByIds(ids);
        if (copied != deleted) {
            throw new IllegalStateException("발송 성공 이력 보관 수와 삭제 수가 다릅니다 보관=" + copied + " 삭제=" + deleted);
        }
        return deleted;
    }
}
