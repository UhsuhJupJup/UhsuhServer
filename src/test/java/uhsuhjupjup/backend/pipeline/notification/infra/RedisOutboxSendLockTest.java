package uhsuhjupjup.backend.pipeline.notification.infra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxSendLock;
import uhsuhjupjup.backend.support.SharedTestContainers;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RedisOutboxSendLockTest {

    static final GenericContainer<?> REDIS = SharedTestContainers.redis();

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redisTemplate;

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
        redisTemplate.delete(RedisOutboxSendLock.KEY_PREFIX + 1);
    }

    @Test
    void 같은_아웃박스의_락은_한_서버만_잡는다() {
        RedisOutboxSendLock serverA = new RedisOutboxSendLock(redisTemplate, Duration.ofMinutes(2));
        RedisOutboxSendLock serverB = new RedisOutboxSendLock(redisTemplate, Duration.ofMinutes(2));

        Optional<OutboxSendLock.Lease> first = serverA.tryAcquire(1L);
        Optional<OutboxSendLock.Lease> second = serverB.tryAcquire(1L);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(redisTemplate.getExpire(RedisOutboxSendLock.KEY_PREFIX + 1)).isPositive();
    }

    @Test
    void 락을_풀면_다른_서버가_잡을_수_있다() {
        RedisOutboxSendLock serverA = new RedisOutboxSendLock(redisTemplate, Duration.ofMinutes(2));
        RedisOutboxSendLock serverB = new RedisOutboxSendLock(redisTemplate, Duration.ofMinutes(2));

        serverA.tryAcquire(1L).orElseThrow().close();

        assertThat(serverB.tryAcquire(1L)).isPresent();
    }

    @Test
    void 만료된_뒤_다른_서버가_잡은_락은_먼저_잡았던_서버가_풀지_못한다() throws InterruptedException {
        RedisOutboxSendLock serverA = new RedisOutboxSendLock(redisTemplate, Duration.ofMillis(200));
        RedisOutboxSendLock serverB = new RedisOutboxSendLock(redisTemplate, Duration.ofMinutes(2));
        OutboxSendLock.Lease expired = serverA.tryAcquire(1L).orElseThrow();
        Thread.sleep(400);
        serverB.tryAcquire(1L).orElseThrow();

        expired.close();

        assertThat(redisTemplate.hasKey(RedisOutboxSendLock.KEY_PREFIX + 1)).isTrue();
        assertThat(serverA.tryAcquire(1L)).isEmpty();
    }
}
