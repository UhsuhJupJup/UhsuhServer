package uhsuhjupjup.backend.pipeline.notification.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
class NotificationOutboxRepositoryTest {

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Test
    void 적재하면_PENDING_상태와_생성시각이_채워진다() {
        NotificationOutbox saved = notificationOutboxRepository.save(
                NotificationOutbox.pending("a@example.com", RecipientType.MEMBER, 3, "제목", "<html>본문</html>",
                        "https://uhsuh.com/unsub?token=t", LocalDateTime.now()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRecipientType()).isEqualTo(RecipientType.MEMBER);
        assertThat(saved.getArticleCount()).isEqualTo(3);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getSentAt()).isNull();
        assertThat(notificationOutboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(1);
    }

    @Test
    void markSent하면_SENT로_바뀌고_발송시각이_기록된다() {
        NotificationOutbox saved = notificationOutboxRepository.save(
                NotificationOutbox.pending("b@example.com", RecipientType.EMAIL_SUBSCRIBER, 1, "제목", "본문",
                        null, LocalDateTime.now()));

        saved.markSent(LocalDateTime.now());
        notificationOutboxRepository.flush();

        NotificationOutbox reloaded = notificationOutboxRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(reloaded.getSentAt()).isNotNull();
        assertThat(reloaded.getLastError()).isNull();
        assertThat(notificationOutboxRepository.countByStatus(OutboxStatus.SENT)).isEqualTo(1);
    }

    @Test
    void markRetry는_PENDING으로_되돌리며_오류와_다음_시도_시각을_기록한다() {
        NotificationOutbox saved = notificationOutboxRepository.save(
                NotificationOutbox.pending("c@example.com", RecipientType.MEMBER, 2, "제목", "본문",
                        null, LocalDateTime.now()));

        saved.markRetry("SMTP timeout", LocalDateTime.now().plusMinutes(1));
        notificationOutboxRepository.flush();

        NotificationOutbox reloaded = notificationOutboxRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getAttempts()).isZero();
        assertThat(reloaded.getLastError()).isEqualTo("SMTP timeout");
    }

    @Test
    void markFailed하면_FAILED로_바뀐다() {
        NotificationOutbox saved = notificationOutboxRepository.save(
                NotificationOutbox.pending("d@example.com", RecipientType.MEMBER, 1, "제목", "본문",
                        null, LocalDateTime.now()));

        saved.markFailed("최대 재시도 초과");
        notificationOutboxRepository.flush();

        NotificationOutbox reloaded = notificationOutboxRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(reloaded.getAttempts()).isZero();
        assertThat(notificationOutboxRepository.countByStatus(OutboxStatus.FAILED)).isEqualTo(1);
    }
}
