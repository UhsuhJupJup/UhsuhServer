package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailSendDailySummary;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailSendLog;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@MySqlDataJpaTest
@Import(OutboxAdminService.class)
class OutboxAdminServiceSentLogTest {

    private static final LocalDateTime OLD_DAY = LocalDateTime.of(2026, 9, 1, 8, 0, 10);
    private static final LocalDateTime RECENT_DAY = LocalDateTime.of(2026, 11, 16, 8, 0, 5);

    @Autowired
    private OutboxAdminService outboxAdminService;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long recentMember;
    private Long recentSubscriber;

    @BeforeEach
    void setUp() {
        recentMember = saveSent("member@example.com", RecipientType.MEMBER, RECENT_DAY);
        recentSubscriber = saveSent("guest@example.com", RecipientType.EMAIL_SUBSCRIBER, RECENT_DAY.plusSeconds(1));
        NotificationOutbox pending = NotificationOutbox.pending("pending@example.com", RecipientType.MEMBER, 1,
                "제목", "본문", null, RECENT_DAY);
        ReflectionTestUtils.setField(pending, "status", OutboxStatus.PENDING);
        notificationOutboxRepository.saveAndFlush(pending);
        archive(900_001L, "old-member@example.com", "MEMBER", OLD_DAY);
        archive(900_002L, "old-guest@example.com", "EMAIL_SUBSCRIBER", OLD_DAY.plusSeconds(2));
    }

    @Test
    void 날짜별_요약은_보관된_이력까지_합친다() {
        List<EmailSendDailySummary> days = outboxAdminService.dailySummary();

        assertThat(days).extracting(EmailSendDailySummary::date, EmailSendDailySummary::total,
                        EmailSendDailySummary::memberCount, EmailSendDailySummary::subscriberCount)
                .containsExactly(
                        tuple("2026-11-16", 2L, 1L, 1L),
                        tuple("2026-09-01", 2L, 1L, 1L));
        assertThat(days.get(1).sentAt()).isEqualTo(OLD_DAY);
    }

    @Test
    void 보관된_날짜를_고르면_보관_테이블의_이력을_돌려준다() {
        List<EmailSendLog> logs = outboxAdminService.sentOn(LocalDate.of(2026, 9, 1));

        assertThat(logs).extracting(EmailSendLog::id, EmailSendLog::email, EmailSendLog::recipientType)
                .containsExactly(
                        tuple(900_002L, "old-guest@example.com", "EMAIL_SUBSCRIBER"),
                        tuple(900_001L, "old-member@example.com", "MEMBER"));
        assertThat(logs.get(1).sentAt()).isEqualTo(OLD_DAY);
        assertThat(logs.get(1).subject()).isEqualTo("보관 제목");
        assertThat(logs.get(1).articleCount()).isEqualTo(3);
    }

    @Test
    void 최근_목록은_두_테이블을_발송_시각_순으로_합치고_대기_중인_알림은_뺀다() {
        List<EmailSendLog> logs = outboxAdminService.recentSent(3);

        assertThat(logs).extracting(EmailSendLog::id)
                .containsExactly(recentSubscriber, recentMember, 900_002L);
    }

    private Long saveSent(String recipient, RecipientType type, LocalDateTime sentAt) {
        NotificationOutbox row = NotificationOutbox.pending(recipient, type, 1, "제목", "본문", null, sentAt);
        row.markSent(sentAt);
        return notificationOutboxRepository.saveAndFlush(row).getId();
    }

    private void archive(Long id, String recipient, String type, LocalDateTime sentAt) {
        jdbcTemplate.update("insert into notification_outbox_archive (id, recipient, recipient_type, subject, body,"
                        + " article_count, status, attempts, next_attempt_at, republish_at, created_at, sent_at, archived_at)"
                        + " values (?, ?, ?, '보관 제목', '본문', 3, 'SENT', 0, ?, ?, ?, ?, ?)",
                id, recipient, type, sentAt, sentAt, sentAt, sentAt, sentAt.plusDays(8));
    }
}
