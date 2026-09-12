package uhsuhjupjup.backend.pipeline;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.matching.application.MatchingService;
import uhsuhjupjup.backend.pipeline.matching.application.dto.MatchingResult;

import java.util.concurrent.locks.Lock;

@Slf4j
@Component
public class ClassificationScheduler {

    private final MatchingService matchingService;
    private final RedisLockRegistry redisLockRegistry;

    ClassificationScheduler(MatchingService matchingService, RedisLockRegistry redisLockRegistry) {
        this.matchingService = matchingService;
        this.redisLockRegistry = redisLockRegistry;
    }

    @Scheduled(fixedDelayString = "${classification.poll-delay-ms:600000}",
            initialDelayString = "${classification.poll-initial-delay-ms:600000}")
    @SchedulerLock(name = "keywordClassification", lockAtMostFor = "${classification.lock-at-most-for:PT9M}")
    public void classifyPending() {
        Lock lock = redisLockRegistry.obtain(PipelineScheduler.CLASSIFICATION_LOCK);
        boolean acquired;
        try {
            acquired = lock.tryLock();
        } catch (Exception e) {
            log.error("분류 락 획득 실패 - 폴러 스킵", e);
            return;
        }
        if (!acquired) {
            log.info("다른 경로가 분류 중 - 폴러 스킵");
            return;
        }
        try {
            MatchingResult result = matchingService.matchRecent();
            log.info("분류 폴러 완료 {}", result);
        } finally {
            lock.unlock();
        }
    }
}
