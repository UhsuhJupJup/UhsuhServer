package uhsuhjupjup.backend.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.redis.util.RedisLockRegistry;
import uhsuhjupjup.backend.pipeline.matching.application.MatchingService;

import java.util.concurrent.locks.Lock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClassificationSchedulerTest {

    @Mock
    private MatchingService matchingService;
    @Mock
    private RedisLockRegistry redisLockRegistry;
    @Mock
    private Lock lock;
    @InjectMocks
    private ClassificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        given(redisLockRegistry.obtain(any())).willReturn(lock);
    }

    @Test
    void 락을_잡으면_미분류_글을_분류하고_락을_푼다() {
        given(lock.tryLock()).willReturn(true);

        scheduler.classifyPending();

        verify(matchingService).matchRecent();
        verify(lock).unlock();
    }

    @Test
    void 다른_경로가_분류_중이면_스킵한다() {
        given(lock.tryLock()).willReturn(false);

        scheduler.classifyPending();

        verify(matchingService, never()).matchRecent();
    }
}
