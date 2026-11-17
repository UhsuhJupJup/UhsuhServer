package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
@Import({OutboxSentCleaner.class, OutboxSentArchiver.class})
@TestPropertySource(properties = {"outbox.sent-retention=P7D", "outbox.cleanup-chunk-size=2"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxSentCleanerTest {

    @Autowired
    private OutboxSentCleaner cleaner;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        notificationOutboxRepository.deleteAllInBatch();
        jdbcTemplate.update("delete from notification_outbox_archive");
    }

    @Test
    void 일주일_지난_발송_성공_이력만_보관_테이블로_옮기고_아웃박스에서_지운다() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<Long> old = IntStream.range(0, 5).mapToObj(i -> saveSent("old" + i + "@example.com", now.minusDays(8))).toList();
        Long recent = saveSent("recent@example.com", now.minusDays(6));
        Long pending = save(OutboxStatus.PENDING, now.minusDays(30));
        Long processing = save(OutboxStatus.PROCESSING, now.minusDays(30));
        Long failed = save(OutboxStatus.FAILED, now.minusDays(30));

        int archived = cleaner.archiveSentBefore(now.minusDays(7), now);

        assertThat(archived).isEqualTo(5);
        assertThat(notificationOutboxRepository.findAll()).extracting(NotificationOutbox::getId)
                .containsExactlyInAnyOrder(recent, pending, processing, failed);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, recipient, recipient_type, subject, body, status, sent_at, archived_at"
                        + " from notification_outbox_archive order by id");
        assertThat(rows).extracting(row -> ((Number) row.get("id")).longValue()).containsExactlyElementsOf(old);
        assertThat(rows.get(0)).containsEntry("recipient", "old0@example.com")
                .containsEntry("recipient_type", "MEMBER")
                .containsEntry("subject", "제목")
                .containsEntry("body", "본문")
                .containsEntry("status", "SENT");
        assertThat(rows.get(0).get("sent_at")).isEqualTo(now.minusDays(8));
        assertThat(rows.get(0).get("archived_at")).isEqualTo(now);
    }

    @Test
    void 옮길_이력이_없으면_아무것도_바꾸지_않는다() {
        saveSent("a@example.com", LocalDateTime.now().minusDays(1));

        assertThat(cleaner.archiveSentBefore(LocalDateTime.now().minusDays(7), LocalDateTime.now())).isZero();
        assertThat(notificationOutboxRepository.count()).isEqualTo(1);
        assertThat(archiveCount()).isZero();
    }

    @Test
    void 보관에_실패하면_아웃박스에서_지우지_않는다() {
        LocalDateTime now = LocalDateTime.now();
        Long id = saveSent("a@example.com", now.minusDays(8));
        jdbcTemplate.update("insert into notification_outbox_archive (id, recipient, recipient_type, subject, body,"
                + " article_count, status, attempts, next_attempt_at, republish_at, created_at, sent_at, archived_at)"
                + " values (?, 'x@example.com', 'MEMBER', '제목', '본문', 1, 'SENT', 0, ?, ?, ?, ?, ?)",
                id, now, now, now, now, now);

        assertThatThrownBy(() -> cleaner.archiveSentBefore(now.minusDays(7), now))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(notificationOutboxRepository.findById(id)).isPresent();
        assertThat(archiveCount()).isEqualTo(1);
    }

    private int archiveCount() {
        return jdbcTemplate.queryForObject("select count(*) from notification_outbox_archive", Integer.class);
    }

    private Long saveSent(String recipient, LocalDateTime sentAt) {
        NotificationOutbox row = NotificationOutbox.pending(recipient, RecipientType.MEMBER, 1, "제목", "본문",
                null, sentAt);
        row.markSent(sentAt);
        return notificationOutboxRepository.save(row).getId();
    }

    private Long save(OutboxStatus status, LocalDateTime createdAt) {
        NotificationOutbox row = NotificationOutbox.pending("a@example.com", RecipientType.MEMBER, 1, "제목", "본문",
                null, createdAt);
        ReflectionTestUtils.setField(row, "status", status);
        return notificationOutboxRepository.save(row).getId();
    }
}
