package uhsuhjupjup.backend.pipeline.notification.infra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxMessageHandler;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxMessageOutcome;
import uhsuhjupjup.backend.support.SharedTestContainers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RedisStreamOutboxConsumerTest {

    static final GenericContainer<?> REDIS = SharedTestContainers.redis();

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redisTemplate;

    private final Map<Long, AtomicInteger> handled = new ConcurrentHashMap<>();
    private RedisStreamOutboxConsumer serverA;
    private RedisStreamOutboxConsumer serverB;

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
    }

    @AfterEach
    void tearDown() {
        if (serverA != null) {
            serverA.stop();
        }
        if (serverB != null) {
            serverB.stop();
        }
    }

    @Test
    void 두_서버가_메시지를_나눠_한_번씩만_처리하고_PEL과_Stream을_비운다() {
        serverA = consumer("server-a", recordingHandler(OutboxMessageOutcome.DONE));
        serverB = consumer("server-b", recordingHandler(OutboxMessageOutcome.DONE));
        serverA.start();
        serverB.start();
        RedisStreamOutboxMessageQueue queue = new RedisStreamOutboxMessageQueue(redisTemplate);

        LongStream.rangeClosed(1, 40).forEach(queue::publish);

        awaitUntil(() -> handled.size() == 40 && streamLength() == 0);
        assertThat(handled.values()).allMatch(count -> count.get() == 1);
        assertThat(pendingCount()).isZero();
        assertThat(streamLength()).isZero();
    }

    @Test
    void 남겨_두라고_한_메시지는_ACK하지_않고_PEL에_남긴다() {
        serverA = consumer("server-a", recordingHandler(OutboxMessageOutcome.KEEP));
        serverA.start();

        new RedisStreamOutboxMessageQueue(redisTemplate).publish(7L);

        awaitUntil(() -> handled.containsKey(7L) && pendingCount() == 1);
        assertThat(pendingCount()).isEqualTo(1);
        assertThat(streamLength()).isEqualTo(1);
    }

    @Test
    void 아웃박스_ID가_없는_메시지는_처리하지_않고_정리한다() {
        serverA = consumer("server-a", recordingHandler(OutboxMessageOutcome.DONE));
        serverA.start();

        redisTemplate.opsForStream().add(OutboxStream.KEY, Map.of("other", "x"));

        awaitUntil(() -> streamLength() == 0);
        assertThat(handled).isEmpty();
        assertThat(pendingCount()).isZero();
    }

    @Test
    void 한_서버가_ACK_없이_멈춘_메시지를_기준_시간이_지나면_다른_서버가_가져가_처리한다() throws InterruptedException {
        serverA = consumer("server-a", recordingHandler(OutboxMessageOutcome.KEEP));
        serverA.start();
        new RedisStreamOutboxMessageQueue(redisTemplate).publish(7L);
        awaitUntil(() -> pendingCount() == 1);
        serverA.stop();
        Map<Long, AtomicInteger> handledByB = new ConcurrentHashMap<>();
        RedisStreamOutboxConsumer reclaimer = new RedisStreamOutboxConsumer(connectionFactory, redisTemplate,
                countingHandler(handledByB, OutboxMessageOutcome.DONE), "server-b",
                Duration.ofMillis(200), 5, Duration.ofMillis(300), 10);
        Thread.sleep(400);

        int reclaimed = reclaimer.reclaim();

        assertThat(reclaimed).isEqualTo(1);
        assertThat(handledByB).containsOnlyKeys(7L);
        assertThat(pendingCount()).isZero();
        assertThat(streamLength()).isZero();
    }

    @Test
    void 기준_시간이_지나지_않은_메시지는_가져가지_않는다() {
        serverA = consumer("server-a", recordingHandler(OutboxMessageOutcome.KEEP));
        serverA.start();
        new RedisStreamOutboxMessageQueue(redisTemplate).publish(7L);
        awaitUntil(() -> pendingCount() == 1);
        Map<Long, AtomicInteger> handledByB = new ConcurrentHashMap<>();
        RedisStreamOutboxConsumer reclaimer = new RedisStreamOutboxConsumer(connectionFactory, redisTemplate,
                countingHandler(handledByB, OutboxMessageOutcome.DONE), "server-b",
                Duration.ofMillis(200), 5, Duration.ofMinutes(1), 10);

        assertThat(reclaimer.reclaim()).isZero();
        assertThat(handledByB).isEmpty();
        assertThat(pendingCount()).isEqualTo(1);
    }

    @Test
    void 재전달_상한을_넘은_메시지는_처리하지_않고_정리한다() throws InterruptedException {
        serverA = consumer("server-a", recordingHandler(OutboxMessageOutcome.KEEP));
        serverA.start();
        new RedisStreamOutboxMessageQueue(redisTemplate).publish(7L);
        awaitUntil(() -> pendingCount() == 1);
        serverA.stop();
        Map<Long, AtomicInteger> handledByB = new ConcurrentHashMap<>();
        RedisStreamOutboxConsumer reclaimer = new RedisStreamOutboxConsumer(connectionFactory, redisTemplate,
                countingHandler(handledByB, OutboxMessageOutcome.DONE), "server-b",
                Duration.ofMillis(200), 5, Duration.ofMillis(300), 1);
        Thread.sleep(400);

        reclaimer.reclaim();

        assertThat(handledByB).isEmpty();
        assertThat(pendingCount()).isZero();
        assertThat(streamLength()).isZero();
    }

    private OutboxMessageHandler countingHandler(Map<Long, AtomicInteger> counts, OutboxMessageOutcome outcome) {
        OutboxMessageHandler handler = mock(OutboxMessageHandler.class);
        given(handler.handle(anyLong())).willAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            counts.computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
            return outcome;
        });
        return handler;
    }

    private OutboxMessageHandler recordingHandler(OutboxMessageOutcome outcome) {
        return countingHandler(handled, outcome);
    }

    private RedisStreamOutboxConsumer consumer(String name, OutboxMessageHandler handler) {
        return new RedisStreamOutboxConsumer(connectionFactory, redisTemplate, handler, name,
                Duration.ofMillis(200), 5, Duration.ofMinutes(5), 10);
    }

    private long streamLength() {
        Long size = redisTemplate.opsForStream().size(OutboxStream.KEY);
        return size == null ? 0 : size;
    }

    private long pendingCount() {
        return redisTemplate.opsForStream().pending(OutboxStream.KEY, OutboxStream.GROUP).getTotalPendingMessages();
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
}
