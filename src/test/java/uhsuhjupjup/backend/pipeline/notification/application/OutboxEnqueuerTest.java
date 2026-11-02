package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OutboxEnqueuerTest {

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    @Mock
    private NotificationSaver notificationSaver;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectProvider<OutboxMessageQueue> outboxMessageQueue;

    @Mock
    private OutboxMessageQueue queue;

    @Test
    void Stream을_쓰면_다시_발행할_시각을_정상_경로_처리_시간만큼_미루고_생성_이벤트를_낸다() {
        given(outboxMessageQueue.getIfAvailable()).willReturn(queue);
        NotificationOutbox saved = enqueue();

        assertThat(saved.getRepublishAt()).isEqualTo(saved.getNextAttemptAt().plusMinutes(10));
        then(eventPublisher).should().publishEvent(any(OutboxCreatedEvent.class));
    }

    @Test
    void Stream을_쓰지_않으면_다시_발행할_시각을_미루지_않는다() {
        NotificationOutbox saved = enqueue();

        assertThat(saved.getRepublishAt()).isEqualTo(saved.getNextAttemptAt());
    }

    private NotificationOutbox enqueue() {
        given(notificationOutboxRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        OutboxEnqueuer enqueuer = new OutboxEnqueuer(notificationOutboxRepository, notificationSaver,
                eventPublisher, outboxMessageQueue);
        ReflectionTestUtils.setField(enqueuer, "republishDelay", Duration.ofMinutes(10));

        enqueuer.enqueueForMember("a@example.com", 1, "제목", "본문", null, 1L, Map.of());

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        then(notificationOutboxRepository).should().save(captor.capture());
        return captor.getValue();
    }
}
