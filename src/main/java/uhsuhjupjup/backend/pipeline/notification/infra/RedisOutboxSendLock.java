package uhsuhjupjup.backend.pipeline.notification.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxSendLock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
class RedisOutboxSendLock implements OutboxSendLock {

    static final String KEY_PREFIX = "uhsuh:notification:outbox:lock:";

    private static final RedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    RedisOutboxSendLock(StringRedisTemplate redisTemplate,
                        @Value("${outbox.send-lock-ttl:PT2M}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public Optional<Lease> tryAcquire(Long outboxId) {
        String key = KEY_PREFIX + outboxId;
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, ttl))) {
            return Optional.empty();
        }
        return Optional.of(() -> release(key, token));
    }

    private void release(String key, String token) {
        try {
            redisTemplate.execute(RELEASE_IF_OWNER, List.of(key), token);
        } catch (RuntimeException e) {
            log.warn("아웃박스 발송 락 해제 실패, 만료로 풀림 key={} 사유={}", key, e.getMessage());
        }
    }
}
