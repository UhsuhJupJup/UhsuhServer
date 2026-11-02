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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OutboxRecoveryPollerTest {

    @Mock
    private OutboxRecoverer outboxRecoverer;

    @Mock
    private OutboxMessageQueue outboxMessageQueue;

    private OutboxRecoveryPoller poller;

    @BeforeEach
    void setUp() {
        poller = new OutboxRecoveryPoller(outboxRecoverer, outboxMessageQueue);
        ReflectionTestUtils.setField(poller, "batchSize", 5000);
    }

    @Test
    void 모은_작업을_Stream에_다시_발행한다() {
        given(outboxRecoverer.collect(anyInt(), any())).willReturn(List.of(1L, 2L));

        poller.recover();

        then(outboxMessageQueue).should().publish(1L);
        then(outboxMessageQueue).should().publish(2L);
    }

    @Test
    void 발행에_실패하면_남은_작업은_다음_복구로_넘긴다() {
        given(outboxRecoverer.collect(anyInt(), any())).willReturn(List.of(1L, 2L));
        willThrow(new IllegalStateException("redis down")).given(outboxMessageQueue).publish(1L);

        poller.recover();

        then(outboxMessageQueue).should(never()).publish(2L);
    }
}
