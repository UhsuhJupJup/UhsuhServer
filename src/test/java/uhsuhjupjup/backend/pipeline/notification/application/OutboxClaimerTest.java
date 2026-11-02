package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
@Import(OutboxClaimer.class)
@TestPropertySource(properties = {"outbox.max-attempts=3", "outbox.claim-lease=PT10M"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxClaimerTest {

    @Autowired
    private OutboxClaimer outboxClaimer;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        notificationOutboxRepository.deleteAllInBatch();
    }

    @Test
    void 다른_서버가_선점_중인_행은_건너뛰고_나머지를_선점한다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<Long> all = saveDue(6, now.minusMinutes(1));
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        CompletableFuture<List<Long>> serverA = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
            List<Long> ids = outboxClaimer.claim(3, now);
            firstClaimed.countDown();
            await(secondDone);
            return ids;
        }));
        assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();

        long startedAt = System.nanoTime();
        List<Long> serverB = outboxClaimer.claim(10, now);
        Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);
        secondDone.countDown();
        List<Long> claimedByA = serverA.get(10, TimeUnit.SECONDS);

        assertThat(waited).isLessThan(Duration.ofSeconds(3));
        assertThat(claimedByA).hasSize(3);
        assertThat(serverB).hasSize(3).doesNotContainAnyElementsOf(claimedByA);
        List<Long> union = new ArrayList<>(claimedByA);
        union.addAll(serverB);
        assertThat(union).containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    void 선점하면_PROCESSING으로_바꾸고_시도횟수와_선점_만료_시각을_기록한다() {
        LocalDateTime now = LocalDateTime.now();
        Long id = saveDue(1, now.minusMinutes(1)).get(0);

        List<Long> claimed = outboxClaimer.claim(10, now);

        NotificationOutbox row = notificationOutboxRepository.findById(id).orElseThrow();
        assertThat(claimed).containsExactly(id);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isAfter(now.plusMinutes(9));
    }

    @Test
    void 기한_전_작업과_선점_중인_작업과_끝난_작업은_선점하지_않는다() {
        LocalDateTime now = LocalDateTime.now();
        saveDue(1, now.plusMinutes(5));
        outboxClaimer.claim(10, now.plusMinutes(6));
        NotificationOutbox sent = notificationOutboxRepository.save(pending(now.minusMinutes(1)));
        sent.markSent(now);
        notificationOutboxRepository.save(sent);
        NotificationOutbox failed = notificationOutboxRepository.save(pending(now.minusMinutes(1)));
        failed.markFailed("영구 실패");
        notificationOutboxRepository.save(failed);
        saveDue(1, now.plusMinutes(30));

        assertThat(outboxClaimer.claim(10, now.plusMinutes(7))).isEmpty();
    }

    @Test
    void 발송_중_서버가_죽어_선점이_만료되면_다시_선점한다() {
        LocalDateTime now = LocalDateTime.now();
        Long id = saveDue(1, now.minusMinutes(1)).get(0);
        outboxClaimer.claim(10, now);

        List<Long> reclaimed = outboxClaimer.claim(10, now.plusMinutes(11));

        NotificationOutbox row = notificationOutboxRepository.findById(id).orElseThrow();
        assertThat(reclaimed).containsExactly(id);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(row.getAttempts()).isEqualTo(2);
    }

    @Test
    void 선점_만료가_시도_한도까지_반복되면_FAILED로_격리한다() {
        LocalDateTime now = LocalDateTime.now();
        Long id = saveDue(1, now.minusMinutes(1)).get(0);
        outboxClaimer.claim(10, now);
        outboxClaimer.claim(10, now.plusMinutes(11));
        outboxClaimer.claim(10, now.plusMinutes(22));

        List<Long> reclaimed = outboxClaimer.claim(10, now.plusMinutes(33));

        NotificationOutbox row = notificationOutboxRepository.findById(id).orElseThrow();
        assertThat(reclaimed).isEmpty();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getLastError()).contains("선점 만료");
    }

    @Test
    void 반납하면_PENDING으로_되돌리고_시도횟수를_원래대로_돌린다() {
        LocalDateTime now = LocalDateTime.now();
        Long id = saveDue(1, now.minusMinutes(1)).get(0);
        outboxClaimer.claim(10, now);

        int released = outboxClaimer.release(List.of(id), now);

        NotificationOutbox row = notificationOutboxRepository.findById(id).orElseThrow();
        assertThat(released).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getAttempts()).isZero();
        assertThat(outboxClaimer.claim(10, now.plusSeconds(1))).containsExactly(id);
    }

    private List<Long> saveDue(int count, LocalDateTime nextAttemptAt) {
        return IntStream.range(0, count)
                .mapToObj(i -> notificationOutboxRepository.save(pending(nextAttemptAt)).getId())
                .toList();
    }

    private NotificationOutbox pending(LocalDateTime nextAttemptAt) {
        return NotificationOutbox.pending("a@example.com", RecipientType.MEMBER, 1, "제목", "본문",
                null, nextAttemptAt);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
