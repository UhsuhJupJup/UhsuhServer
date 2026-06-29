package uhsuhjupjup.backend.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.collection.application.CollectionService;
import uhsuhjupjup.backend.matching.application.MatchingService;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PipelineSchedulerTest {

    @Mock
    private CollectionService collectionService;

    @Mock
    private MatchingService matchingService;

    @InjectMocks
    private PipelineScheduler scheduler;

    @Test
    void run_runsCollectionThenMatching() {
        scheduler.run();

        InOrder inOrder = inOrder(collectionService, matchingService);
        inOrder.verify(collectionService).collectAll();
        inOrder.verify(matchingService).matchRecent();
    }

    @Test
    void run_whenCollectionFails_stillRunsMatching() {
        given(collectionService.collectAll()).willThrow(new RuntimeException("boom"));

        scheduler.run();

        verify(matchingService).matchRecent();
    }
}
