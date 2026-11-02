package uhsuhjupjup.backend.pipeline.notification.infra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import uhsuhjupjup.backend.pipeline.notification.application.FakeEmailSender;
import uhsuhjupjup.backend.pipeline.notification.application.FakeOutboxSendLock;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxClaimer;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxDispatcher;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxMessageHandler;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxRecoverer;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxRecoveryPoller;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxResultRecorder;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;
import uhsuhjupjup.backend.support.SharedTestContainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
@Import({OutboxMessageHandler.class, OutboxClaimer.class, OutboxDispatcher.class, OutboxResultRecorder.class,
        OutboxRecoverer.class, OutboxRecoveryFlowTest.Fakes.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxRecoveryFlowTest {

    static final GenericContainer<?> REDIS = SharedTestContainers.redis();

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redisTemplate;

    @Autowired
    private OutboxMessageHandler handler;

    @Autowired
    private OutboxRecoverer recoverer;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private FakeEmailSender emailSender;

    private RedisStreamOutboxConsumer consumer;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redisTemplate.delete(OutboxStream.KEY);
        emailSender.sent().clear();
        consumer = new RedisStreamOutboxConsumer(connectionFactory, redisTemplate, handler, "server-a",
                Duration.ofMillis(200), 5);
        consumer.start();
    }

    @AfterEach
    void tearDown() {
        consumer.stop();
        notificationOutboxRepository.deleteAllInBatch();
    }

    @Test
    void 커밋_뒤_메시지를_잃은_작업을_복구_폴러가_다시_발행해_발송한다() {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(20);
        NotificationOutbox lost = NotificationOutbox.pending("lost@example.com", RecipientType.MEMBER, 1, "제목",
                "본문", null, createdAt);
        lost.deferRepublish(createdAt.plusMinutes(10));
        Long id = notificationOutboxRepository.save(lost).getId();
        OutboxRecoveryPoller poller = new OutboxRecoveryPoller(recoverer,
                new RedisStreamOutboxMessageQueue(redisTemplate));
        ReflectionTestUtils.setField(poller, "batchSize", 100);

        poller.recover();

        awaitUntil(() -> notificationOutboxRepository.findById(id).orElseThrow().getStatus() == OutboxStatus.SENT);
        assertThat(emailSender.sent()).extracting(m -> m.to()).containsExactly("lost@example.com");
        awaitUntil(() -> redisTemplate.opsForStream().size(OutboxStream.KEY) == 0);
    }

    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("15초 안에 조건을 만족하지 못함");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {

        @Bean
        FakeEmailSender fakeEmailSender() {
            return new FakeEmailSender();
        }

        @Bean
        FakeOutboxSendLock fakeOutboxSendLock() {
            return new FakeOutboxSendLock();
        }
    }
}
