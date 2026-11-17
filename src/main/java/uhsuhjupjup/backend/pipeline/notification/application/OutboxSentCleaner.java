package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxSentCleaner {

    private final OutboxSentArchiver outboxSentArchiver;

    @Value("${outbox.sent-retention:P7D}")
    private Duration sentRetention;

    @Value("${outbox.cleanup-chunk-size:1000}")
    private int chunkSize;

    @Scheduled(cron = "${outbox.cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "notificationOutboxCleanup", lockAtMostFor = "PT30M")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        int archived = archiveSentBefore(now.minus(sentRetention), now);
        log.info("발송 성공 이력 보관 이동={} 아웃박스 보관 기간={}", archived, sentRetention);
    }

    public int archiveSentBefore(LocalDateTime sentBefore, LocalDateTime archivedAt) {
        int total = 0;
        int moved;
        do {
            moved = outboxSentArchiver.archiveChunk(sentBefore, chunkSize, archivedAt);
            total += moved;
        } while (moved == chunkSize);
        return total;
    }
}
