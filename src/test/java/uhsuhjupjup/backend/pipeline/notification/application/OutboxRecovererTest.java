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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
@Import({OutboxRecoverer.class, OutboxClaimer.class})
@TestPropertySource(properties = {"outbox.max-attempts=3", "outbox.republish-delay=PT10M"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxRecovererTest {

    @Autowired
    private OutboxRecoverer outboxRecoverer;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @AfterEach
    void tearDown() {
        notificationOutboxRepository.deleteAllInBatch();
    }

    @Test
    void 정해진_시간_안에_처리되지_않은_PENDING을_모으고_다시_발행할_시각을_미룬다() {
        LocalDateTime now = LocalDateTime.now();
        Long lost = save(OutboxStatus.PENDING, 0, now.minusMinutes(15), now.minusMinutes(5));

        List<Long> collected = outboxRecoverer.collect(10, now);

        assertThat(collected).containsExactly(lost);
        assertThat(reload(lost).getRepublishAt()).isAfter(now.plusMinutes(9));
        assertThat(outboxRecoverer.collect(10, now.plusMinutes(1))).isEmpty();
    }

    @Test
    void 방금_발행된_정상_경로의_작업은_모으지_않는다() {
        LocalDateTime now = LocalDateTime.now();
        save(OutboxStatus.PENDING, 0, now, now.plusMinutes(10));

        assertThat(outboxRecoverer.collect(10, now.plusMinutes(1))).isEmpty();
    }

    @Test
    void 백오프_중인_작업은_재시도_시각이_되기_전에는_모으지_않는다() {
        LocalDateTime now = LocalDateTime.now();
        Long retry = save(OutboxStatus.PENDING, 1, now.plusMinutes(5), now.plusMinutes(5));

        assertThat(outboxRecoverer.collect(10, now)).isEmpty();
        assertThat(outboxRecoverer.collect(10, now.plusMinutes(6))).containsExactly(retry);
    }

    @Test
    void 선점_만료가_지난_PROCESSING만_모으고_상태는_그대로_둔다() {
        LocalDateTime now = LocalDateTime.now();
        Long expired = save(OutboxStatus.PROCESSING, 1, now.minusMinutes(1), now.minusMinutes(20));
        save(OutboxStatus.PROCESSING, 1, now.plusMinutes(5), now.minusMinutes(20));

        List<Long> collected = outboxRecoverer.collect(10, now);

        assertThat(collected).containsExactly(expired);
        assertThat(reload(expired).getStatus()).isEqualTo(OutboxStatus.PROCESSING);
    }

    @Test
    void 선점_만료가_시도_한도에_이른_작업은_FAILED로_격리하고_발행하지_않는다() {
        LocalDateTime now = LocalDateTime.now();
        Long exhausted = save(OutboxStatus.PROCESSING, 3, now.minusMinutes(1), now.minusMinutes(20));

        assertThat(outboxRecoverer.collect(10, now)).isEmpty();
        assertThat(reload(exhausted).getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void 끝난_작업은_모으지_않는다() {
        LocalDateTime now = LocalDateTime.now();
        save(OutboxStatus.SENT, 1, now.minusMinutes(30), now.minusMinutes(30));
        save(OutboxStatus.FAILED, 3, now.minusMinutes(30), now.minusMinutes(30));

        assertThat(outboxRecoverer.collect(10, now)).isEmpty();
    }

    private Long save(OutboxStatus status, int attempts, LocalDateTime nextAttemptAt, LocalDateTime republishAt) {
        NotificationOutbox row = NotificationOutbox.pending("a@example.com", RecipientType.MEMBER, 1, "제목", "본문",
                null, nextAttemptAt);
        row.deferRepublish(republishAt);
        ReflectionTestUtils.setField(row, "status", status);
        ReflectionTestUtils.setField(row, "attempts", attempts);
        return notificationOutboxRepository.save(row).getId();
    }

    private NotificationOutbox reload(Long id) {
        return notificationOutboxRepository.findById(id).orElseThrow();
    }
}
