package uhsuhjupjup.backend.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.collection.application.CollectionService;
import uhsuhjupjup.backend.matching.application.MatchingService;
import uhsuhjupjup.backend.notification.application.NotificationService;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PipelineSchedulerTest {

    @Mock
    private CollectionService collectionService;

    @Mock
    private MatchingService matchingService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PipelineScheduler scheduler;

    @Test
    void run_runsCollectThenMatchThenNotify() {
        scheduler.run();

        InOrder inOrder = inOrder(collectionService, matchingService, notificationService);
        inOrder.verify(collectionService).collectAll();
        inOrder.verify(matchingService).matchRecent();
        inOrder.verify(notificationService).notifyRecent();
    }

    @Test
    void run_whenAStageFails_laterStagesStillRun() {
        given(matchingService.matchRecent()).willThrow(new RuntimeException("boom"));

        scheduler.run();

        verify(notificationService).notifyRecent();
    }
}
