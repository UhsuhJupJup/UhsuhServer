package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
@Import({OutboxMessageHandler.class, OutboxClaimer.class, OutboxDispatcher.class, OutboxResultRecorder.class,
        OutboxMessageHandlerTest.FakeEmailSenderConfig.class})
@TestPropertySource(properties = {"outbox.max-attempts=3", "outbox.claim-lease=PT10M"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxMessageHandlerTest {

    @Autowired
    private OutboxMessageHandler handler;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private FakeEmailSender emailSender;

    @BeforeEach
    void setUp() {
        emailSender.sent().clear();
    }

    @AfterEach
    void tearDown() {
        notificationOutboxRepository.deleteAllInBatch();
    }

    @Test
    void 메시지를_받으면_선점해_발송하고_SENT로_기록한다() {
        Long id = save("a@example.com");

        OutboxMessageOutcome outcome = handler.handle(id);

        assertThat(outcome).isEqualTo(OutboxMessageOutcome.DONE);
        assertThat(emailSender.sent()).extracting(m -> m.to()).containsExactly("a@example.com");
        NotificationOutbox row = notificationOutboxRepository.findById(id).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void 같은_작업의_메시지가_다시_와도_한_번만_발송한다() {
        Long id = save("a@example.com");

        handler.handle(id);
        OutboxMessageOutcome second = handler.handle(id);

        assertThat(second).isEqualTo(OutboxMessageOutcome.DONE);
        assertThat(emailSender.sent()).hasSize(1);
    }

    @Test
    void 같은_작업의_메시지를_두_서버가_동시에_받아도_한_번만_발송한다() throws Exception {
        Long id = save("a@example.com");
        CountDownLatch start = new CountDownLatch(1);

        List<CompletableFuture<OutboxMessageOutcome>> servers = List.of(
                CompletableFuture.supplyAsync(() -> awaitThenHandle(start, id)),
                CompletableFuture.supplyAsync(() -> awaitThenHandle(start, id)));
        start.countDown();
        for (CompletableFuture<OutboxMessageOutcome> server : servers) {
            server.get(10, TimeUnit.SECONDS);
        }

        assertThat(emailSender.sent()).hasSize(1);
    }

    @Test
    void 다른_서버가_선점해_발송_중이면_메시지를_남겨_둔다() {
        NotificationOutbox processing = save(OutboxStatus.PROCESSING, LocalDateTime.now().plusMinutes(5));

        OutboxMessageOutcome outcome = handler.handle(processing.getId());

        assertThat(outcome).isEqualTo(OutboxMessageOutcome.KEEP);
        assertThat(emailSender.sent()).isEmpty();
    }

    @Test
    void 일시_실패하면_다음_시도_시각을_적고_메시지는_정리한다() {
        emailSender.failFor("fail@example.com");
        Long id = save("fail@example.com");

        OutboxMessageOutcome outcome = handler.handle(id);
        OutboxMessageOutcome duplicate = handler.handle(id);

        NotificationOutbox row = notificationOutboxRepository.findById(id).orElseThrow();
        assertThat(outcome).isEqualTo(OutboxMessageOutcome.DONE);
        assertThat(duplicate).isEqualTo(OutboxMessageOutcome.DONE);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void 아웃박스_행이_없으면_메시지를_정리한다() {
        assertThat(handler.handle(999_999L)).isEqualTo(OutboxMessageOutcome.DONE);
        assertThat(emailSender.sent()).isEmpty();
    }

    private OutboxMessageOutcome awaitThenHandle(CountDownLatch start, Long id) {
        try {
            start.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return handler.handle(id);
    }

    private Long save(String recipient) {
        return notificationOutboxRepository.save(NotificationOutbox.pending(recipient, RecipientType.MEMBER, 1,
                "제목", "본문", null, LocalDateTime.now())).getId();
    }

    private NotificationOutbox save(OutboxStatus status, LocalDateTime nextAttemptAt) {
        NotificationOutbox row = NotificationOutbox.pending("busy@example.com", RecipientType.MEMBER, 1,
                "제목", "본문", null, nextAttemptAt);
        ReflectionTestUtils.setField(row, "status", status);
        ReflectionTestUtils.setField(row, "attempts", 1);
        return notificationOutboxRepository.save(row);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeEmailSenderConfig {

        @Bean
        FakeEmailSender fakeEmailSender() {
            return new FakeEmailSender();
        }
    }
}
