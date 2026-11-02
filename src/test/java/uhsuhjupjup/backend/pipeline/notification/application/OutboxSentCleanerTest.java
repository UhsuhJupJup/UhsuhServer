package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
@Import(OutboxSentCleaner.class)
@TestPropertySource(properties = {"outbox.sent-retention=P7D", "outbox.cleanup-chunk-size=2"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxSentCleanerTest {

    @Autowired
    private OutboxSentCleaner cleaner;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @AfterEach
    void tearDown() {
        notificationOutboxRepository.deleteAllInBatch();
    }

    @Test
    void 일주일_지난_발송_성공_이력만_나눠서_지운다() {
        LocalDateTime now = LocalDateTime.now();
        IntStream.range(0, 5).forEach(i -> saveSent(now.minusDays(8)));
        Long recent = saveSent(now.minusDays(6));
        Long pending = save(OutboxStatus.PENDING, now.minusDays(30));
        Long processing = save(OutboxStatus.PROCESSING, now.minusDays(30));
        Long failed = save(OutboxStatus.FAILED, now.minusDays(30));

        int deleted = cleaner.deleteSentBefore(now.minusDays(7));

        assertThat(deleted).isEqualTo(5);
        assertThat(notificationOutboxRepository.findAll()).extracting(NotificationOutbox::getId)
                .containsExactlyInAnyOrder(recent, pending, processing, failed);
    }

    @Test
    void 지울_이력이_없으면_아무것도_지우지_않는다() {
        saveSent(LocalDateTime.now().minusDays(1));

        assertThat(cleaner.deleteSentBefore(LocalDateTime.now().minusDays(7))).isZero();
        assertThat(notificationOutboxRepository.count()).isEqualTo(1);
    }

    private Long saveSent(LocalDateTime sentAt) {
        NotificationOutbox row = NotificationOutbox.pending("a@example.com", RecipientType.MEMBER, 1, "제목", "본문",
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
