package uhsuhjupjup.backend.collection.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.collection.application.CollectionService;

@Component
@RequiredArgsConstructor
public class CollectionScheduler {

    private final CollectionService collectionService;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void collect() {
        collectionService.collectAll();
    }
}
