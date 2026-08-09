package uhsuhjupjup.backend.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.redis.util.RedisLockRegistry;
import uhsuhjupjup.backend.pipeline.collection.application.CollectionService;
import uhsuhjupjup.backend.pipeline.matching.application.MatchingService;
import uhsuhjupjup.backend.pipeline.notification.application.NotificationService;
import uhsuhjupjup.backend.pipeline.run.application.PipelineRunRecorder;

import java.util.concurrent.locks.Lock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Mock
    private RedisLockRegistry redisLockRegistry;

    @Mock
    private Lock lock;

    @InjectMocks
    private PipelineScheduler scheduler;

    @BeforeEach
    void setUp() {
        // 단일 인스턴스: 락은 항상 획득한다고 가정
        given(redisLockRegistry.obtain(any())).willReturn(lock);
        given(lock.tryLock()).willReturn(true);
    }

    @Test
    void ingest_runsCollectThenMatch_thenRecords() {
        scheduler.ingest();

        InOrder inOrder = inOrder(collectionService, matchingService, pipelineRunRecorder);
        inOrder.verify(collectionService).collectAll();
        inOrder.verify(matchingService).matchRecent();
        inOrder.verify(pipelineRunRecorder).recordIngest(any(), any(), any(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void ingest_whenMatchFails_stillRecords() {
        given(matchingService.matchRecent()).willThrow(new RuntimeException("boom"));

        scheduler.ingest();

        verify(pipelineRunRecorder).recordIngest(any(), any(), any(), any());
    }

    @Test
    void notifyMembers_runsNotification_thenRecords() {
        scheduler.notifyMembers();

        verify(notificationService).notifyRecent();
        verify(pipelineRunRecorder).recordNotification(any(), any(), any());
        verifyNoInteractions(collectionService, matchingService);
    }
}
