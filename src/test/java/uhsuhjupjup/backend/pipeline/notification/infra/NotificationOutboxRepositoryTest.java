package uhsuhjupjup.backend.pipeline.notification.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import uhsuhjupjup.backend.config.JpaAuditingConfig;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class NotificationOutboxRepositoryTest extends MySqlTestSupport {

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
    void markRetry는_PENDING을_유지하며_시도횟수와_오류를_기록한다() {
        NotificationOutbox saved = notificationOutboxRepository.save(
                NotificationOutbox.pending("c@example.com", RecipientType.MEMBER, 2, "제목", "본문",
                        null, LocalDateTime.now()));

        saved.markRetry("SMTP timeout", LocalDateTime.now().plusMinutes(1));
        notificationOutboxRepository.flush();

        NotificationOutbox reloaded = notificationOutboxRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
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
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(notificationOutboxRepository.countByStatus(OutboxStatus.FAILED)).isEqualTo(1);
    }

    @Test
    void findDue는_기한이_지난_PENDING만_이른순으로_조회한다() {
        LocalDateTime now = LocalDateTime.now();
        NotificationOutbox due = notificationOutboxRepository.save(
                NotificationOutbox.pending("due@example.com", RecipientType.MEMBER, 1, "제목", "본문",
                        null, now.minusMinutes(1)));
        notificationOutboxRepository.save(
                NotificationOutbox.pending("future@example.com", RecipientType.MEMBER, 1, "제목", "본문",
                        null, now.plusMinutes(10)));
        NotificationOutbox sent = notificationOutboxRepository.save(
                NotificationOutbox.pending("sent@example.com", RecipientType.MEMBER, 1, "제목", "본문",
                        null, now.minusMinutes(5)));
        sent.markSent(now);
        notificationOutboxRepository.flush();

        List<NotificationOutbox> found = notificationOutboxRepository.findDue(
                OutboxStatus.PENDING, now, PageRequest.of(0, 10));

        assertThat(found).extracting(NotificationOutbox::getId).containsExactly(due.getId());
    }
}
