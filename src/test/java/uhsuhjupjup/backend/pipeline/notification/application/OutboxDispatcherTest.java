package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    private FakeEmailSender emailSender;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        emailSender = new FakeEmailSender();
        dispatcher = new OutboxDispatcher(notificationOutboxRepository, emailSender);
        ReflectionTestUtils.setField(dispatcher, "maxAttempts", 5);
        ReflectionTestUtils.setField(dispatcher, "retryBackoffBase", Duration.ofMinutes(1));
        ReflectionTestUtils.setField(dispatcher, "retryBackoffCap", Duration.ofMinutes(30));
    }

    private NotificationOutbox outbox(String recipient) {
        NotificationOutbox row = NotificationOutbox.pending(recipient, RecipientType.MEMBER, 2,
                "제목", "<html>", "https://uhsuh/u", LocalDateTime.now());
        ReflectionTestUtils.setField(row, "id", 1L);
        return row;
    }

    @Test
    void 발송_성공하면_SENT로_전이한다() {
        NotificationOutbox row = outbox("a@test.com");
        given(notificationOutboxRepository.findById(1L)).willReturn(Optional.of(row));

        boolean result = dispatcher.dispatchOne(1L);

        assertThat(result).isTrue();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(row.getSentAt()).isNotNull();
        assertThat(emailSender.sent()).hasSize(1);
        assertThat(emailSender.sent().get(0).to()).isEqualTo("a@test.com");
    }

    @Test
    void 발송_실패하면_재시도로_예약하고_PENDING을_유지한다() {
        NotificationOutbox row = outbox("fail@test.com");
        emailSender.failFor("fail@test.com");
        given(notificationOutboxRepository.findById(1L)).willReturn(Optional.of(row));

        boolean result = dispatcher.dispatchOne(1L);

        assertThat(result).isFalse();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(row.getLastError()).contains("boom");
    }

    @Test
    void 영구_오류는_재시도없이_즉시_FAILED로_전이한다() {
        NotificationOutbox row = outbox("bad@test.com");
        emailSender.failPermanentlyFor("bad@test.com");
        given(notificationOutboxRepository.findById(1L)).willReturn(Optional.of(row));

        boolean result = dispatcher.dispatchOne(1L);

        assertThat(result).isFalse();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void 최대_시도에_도달하면_FAILED로_전이한다() {
        NotificationOutbox row = outbox("fail@test.com");
        ReflectionTestUtils.setField(row, "attempts", 4);
        emailSender.failFor("fail@test.com");
        given(notificationOutboxRepository.findById(1L)).willReturn(Optional.of(row));

        boolean result = dispatcher.dispatchOne(1L);

        assertThat(result).isFalse();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(5);
    }

    @Test
    void 이미_처리된_행은_건너뛴다() {
        NotificationOutbox row = outbox("a@test.com");
        row.markSent(LocalDateTime.now());
        given(notificationOutboxRepository.findById(1L)).willReturn(Optional.of(row));

        boolean result = dispatcher.dispatchOne(1L);

        assertThat(result).isFalse();
        assertThat(emailSender.sent()).isEmpty();
    }

    @Test
    void 존재하지_않는_행은_false를_반환한다() {
        given(notificationOutboxRepository.findById(99L)).willReturn(Optional.empty());

        boolean result = dispatcher.dispatchOne(99L);

        assertThat(result).isFalse();
        assertThat(emailSender.sent()).isEmpty();
    }
}
