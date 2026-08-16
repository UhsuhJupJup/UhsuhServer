package uhsuhjupjup.backend.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.learningnote.application.GlobalKeywordGraphProvider;
import uhsuhjupjup.backend.pipeline.collection.application.CollectionService;
import uhsuhjupjup.backend.pipeline.collection.application.dto.CollectionResult;
import uhsuhjupjup.backend.pipeline.matching.application.MatchingService;
import uhsuhjupjup.backend.pipeline.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.pipeline.notification.application.NotificationService;
import uhsuhjupjup.backend.pipeline.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.pipeline.run.application.PipelineRunRecorder;

import java.time.LocalDateTime;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineScheduler {

    private final CollectionService collectionService;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final PipelineRunRecorder pipelineRunRecorder;
    private final RedisLockRegistry redisLockRegistry;
    private final GlobalKeywordGraphProvider globalKeywordGraphProvider;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void ingest() {
        runWithLock("pipeline:ingest", () -> {
            LocalDateTime startedAt = LocalDateTime.now();
            CollectionResult collection = runStage("수집", collectionService::collectAll);
            MatchingResult matching = runStage("매칭", matchingService::matchRecent);
            pipelineRunRecorder.recordIngest(startedAt, LocalDateTime.now(), collection, matching);
            runStage("그래프 캐시 선계산", () -> {
                globalKeywordGraphProvider.refreshGlobalGraph();
                return null;
            });
        });
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void notifyMembers() {
        runWithLock("pipeline:notify", () -> {
            LocalDateTime startedAt = LocalDateTime.now();
            NotificationResult notification = runStage("발송", notificationService::notifyRecent);
            pipelineRunRecorder.recordNotification(startedAt, LocalDateTime.now(), notification);
        });
    }

    private <T> T runStage(String name, Supplier<T> stage) {
        try {
            return stage.get();
        } catch (Exception e) {
            log.error("파이프라인 단계 실패 stage={}", name, e);
            return null;
        }
    }

    private void runWithLock(String key, Runnable task) {
        Lock lock = redisLockRegistry.obtain(key);
        boolean acquired;
        try {
            acquired = lock.tryLock();
        } catch (Exception e) {
            log.error("락 획득 실패 - 스킵 key={}", key, e);
            return;
        }
        if (!acquired) {
            log.info("다른 인스턴스가 실행 중 - 스킵 key={}", key);
            return;
        }

        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }
}
