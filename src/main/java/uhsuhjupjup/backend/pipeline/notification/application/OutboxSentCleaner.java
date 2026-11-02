package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxSentCleaner {

    private final NotificationOutboxRepository notificationOutboxRepository;

    @Value("${outbox.sent-retention:P7D}")
    private Duration sentRetention;

    @Value("${outbox.cleanup-chunk-size:1000}")
    private int chunkSize;

    @Scheduled(cron = "${outbox.cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "notificationOutboxCleanup", lockAtMostFor = "PT30M")
    public void cleanup() {
        int deleted = deleteSentBefore(LocalDateTime.now().minus(sentRetention));
        log.info("발송 성공 이력 정리 삭제={} 보관={}", deleted, sentRetention);
    }

    public int deleteSentBefore(LocalDateTime before) {
        int total = 0;
        int deleted;
        do {
            deleted = notificationOutboxRepository.deleteSentBefore(before, chunkSize);
            total += deleted;
        } while (deleted == chunkSize);
        return total;
    }
}
