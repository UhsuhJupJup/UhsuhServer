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
        OutboxResultRecorder recorder = new OutboxResultRecorder(notificationOutboxRepository);
        ReflectionTestUtils.setField(recorder, "maxAttempts", 5);
        ReflectionTestUtils.setField(recorder, "retryBackoffBase", Duration.ofMinutes(1));
        ReflectionTestUtils.setField(recorder, "retryBackoffCap", Duration.ofMinutes(30));
        dispatcher = new OutboxDispatcher(notificationOutboxRepository, emailSender, recorder);
    }

    private NotificationOutbox claimed(String recipient, int attempts) {
        NotificationOutbox row = NotificationOutbox.pending(recipient, RecipientType.MEMBER, 2,
                "제목", "<html>", "https://uhsuh/u", LocalDateTime.now());
        ReflectionTestUtils.setField(row, "id", 1L);
        ReflectionTestUtils.setField(row, "status", OutboxStatus.PROCESSING);
        ReflectionTestUtils.setField(row, "attempts", attempts);
        given(notificationOutboxRepository.findById(1L)).willReturn(Optional.of(row));
        return row;
    }

    @Test
    void 발송_성공하면_SENT로_전이한다() {
        NotificationOutbox row = claimed("a@test.com", 1);

        boolean result = dispatcher.dispatchClaimed(1L);

        assertThat(result).isTrue();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(row.getSentAt()).isNotNull();
        assertThat(emailSender.sent()).hasSize(1);
        assertThat(emailSender.sent().get(0).to()).isEqualTo("a@test.com");
    }

    @Test
    void 첫_실패는_기본_간격_뒤로_재시도를_예약하고_PENDING으로_되돌린다() {
        NotificationOutbox row = claimed("fail@test.com", 1);
        emailSender.failFor("fail@test.com");
        LocalDateTime before = LocalDateTime.now();

        boolean result = dispatcher.dispatchClaimed(1L);

        assertThat(result).isFalse();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isBetween(before.plusMinutes(1), LocalDateTime.now().plusMinutes(1));
        assertThat(row.getLastError()).contains("boom");
    }

    @Test
    void 실패가_쌓이면_간격이_두_배씩_늘어난다() {
        NotificationOutbox row = claimed("fail@test.com", 3);
        emailSender.failFor("fail@test.com");
        LocalDateTime before = LocalDateTime.now();

        dispatcher.dispatchClaimed(1L);

        assertThat(row.getNextAttemptAt()).isBetween(before.plusMinutes(4), LocalDateTime.now().plusMinutes(4));
    }

    @Test
    void 영구_오류는_재시도없이_즉시_FAILED로_전이한다() {
        NotificationOutbox row = claimed("bad@test.com", 1);
        emailSender.failPermanentlyFor("bad@test.com");

        boolean result = dispatcher.dispatchClaimed(1L);

        assertThat(result).isFalse();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void 최대_시도에_도달하면_FAILED로_전이한다() {
        NotificationOutbox row = claimed("fail@test.com", 5);
        emailSender.failFor("fail@test.com");

        boolean result = dispatcher.dispatchClaimed(1L);

        assertThat(result).isFalse();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(5);
    }

    @Test
    void 선점하지_않은_행은_건너뛴다() {
        NotificationOutbox row = claimed("a@test.com", 1);
        row.markSent(LocalDateTime.now());

        boolean result = dispatcher.dispatchClaimed(1L);

        assertThat(result).isFalse();
        assertThat(emailSender.sent()).isEmpty();
    }

    @Test
    void 존재하지_않는_행은_false를_반환한다() {
        given(notificationOutboxRepository.findById(99L)).willReturn(Optional.empty());

        boolean result = dispatcher.dispatchClaimed(99L);

        assertThat(result).isFalse();
        assertThat(emailSender.sent()).isEmpty();
    }
}
