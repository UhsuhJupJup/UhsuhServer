package uhsuhjupjup.backend.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.collection.application.CollectionService;
import uhsuhjupjup.backend.matching.application.MatchingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineScheduler {

    private final CollectionService collectionService;
    private final MatchingService matchingService;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void run() {
        runStage("수집", collectionService::collectAll);
        runStage("매칭", matchingService::matchRecent);
    }

    private void runStage(String name, Runnable stage) {
        try {
            stage.run();
        } catch (Exception e) {
            log.error("파이프라인 단계 실패 stage={}", name, e);
        }
    }
}
