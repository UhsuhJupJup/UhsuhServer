package uhsuhjupjup.backend.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.collection.application.CollectionService;
import uhsuhjupjup.backend.pipeline.collection.application.dto.CollectionResult;
import uhsuhjupjup.backend.pipeline.matching.application.MatchingService;
import uhsuhjupjup.backend.pipeline.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.pipeline.notification.application.NotificationService;
import uhsuhjupjup.backend.pipeline.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.pipeline.run.application.PipelineRunRecorder;

import java.time.LocalDateTime;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineScheduler {

    private final CollectionService collectionService;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final PipelineRunRecorder pipelineRunRecorder;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDateTime startedAt = LocalDateTime.now();
        CollectionResult collection = runStage("수집", collectionService::collectAll);
        MatchingResult matching = runStage("매칭", matchingService::matchRecent);
        NotificationResult notification = runStage("발송", notificationService::notifyRecent);
        pipelineRunRecorder.record(startedAt, LocalDateTime.now(), collection, matching, notification);
    }

    private <T> T runStage(String name, Supplier<T> stage) {
        try {
            return stage.get();
        } catch (Exception e) {
            log.error("파이프라인 단계 실패 stage={}", name, e);
            return null;
        }
    }
}
