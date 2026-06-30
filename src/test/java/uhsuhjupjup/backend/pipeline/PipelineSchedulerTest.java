package uhsuhjupjup.backend.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.pipeline.collection.application.CollectionService;
import uhsuhjupjup.backend.pipeline.matching.application.MatchingService;
import uhsuhjupjup.backend.pipeline.notification.application.NotificationService;
import uhsuhjupjup.backend.pipeline.run.application.PipelineRunRecorder;

import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private PipelineRunRecorder pipelineRunRecorder;

    @InjectMocks
    private PipelineScheduler scheduler;

    @Test
    void run_runsCollectThenMatchThenNotify_thenRecords() {
        scheduler.run();

        InOrder inOrder = inOrder(collectionService, matchingService, notificationService, pipelineRunRecorder);
        inOrder.verify(collectionService).collectAll();
        inOrder.verify(matchingService).matchRecent();
        inOrder.verify(notificationService).notifyRecent();
        inOrder.verify(pipelineRunRecorder).record(any(), any(), any(), any(), any());
    }

    @Test
    void run_whenAStageFails_laterStagesStillRunAndRunIsRecorded() {
        given(matchingService.matchRecent()).willThrow(new RuntimeException("boom"));

        scheduler.run();

        verify(notificationService).notifyRecent();
        verify(pipelineRunRecorder).record(any(), any(), any(), any(), any());
    }
}
