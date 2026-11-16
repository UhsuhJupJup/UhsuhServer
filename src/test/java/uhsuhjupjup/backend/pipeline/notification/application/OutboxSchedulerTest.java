package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @Mock
    private OutboxClaimer outboxClaimer;

    @Mock
    private OutboxDispatcher outboxDispatcher;

    private OutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxScheduler(outboxClaimer, outboxDispatcher);
        ReflectionTestUtils.setField(scheduler, "batchSize", 5000);
    }

    @Test
    void 선점한_작업을_차례로_발송한다() {
        given(outboxClaimer.claim(anyInt(), any())).willReturn(List.of(1L, 2L, 3L));

        scheduler.dispatch();

        then(outboxDispatcher).should().dispatchClaimed(1L);
        then(outboxDispatcher).should().dispatchClaimed(2L);
        then(outboxDispatcher).should().dispatchClaimed(3L);
        then(outboxClaimer).should(never()).release(any(), any());
    }

    @Test
    void 종료가_시작되면_남은_선점을_반납한다() {
        given(outboxClaimer.claim(anyInt(), any())).willReturn(List.of(1L, 2L, 3L));
        given(outboxDispatcher.dispatchClaimed(1L)).willAnswer(invocation -> {
            scheduler.stop();
            return true;
        });

        scheduler.dispatch();

        then(outboxDispatcher).should(never()).dispatchClaimed(2L);
        then(outboxClaimer).should().release(eq(List.of(2L, 3L)), any());
    }

    @Test
    void 종료가_시작된_뒤에는_새로_선점하지_않는다() {
        scheduler.stop();

        scheduler.dispatch();

        then(outboxClaimer).should(never()).claim(anyInt(), any());
    }
}
