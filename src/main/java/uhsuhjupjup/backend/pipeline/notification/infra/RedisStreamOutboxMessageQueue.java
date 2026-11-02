package uhsuhjupjup.backend.pipeline.notification.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxMessageQueue;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "outbox.stream.enabled", havingValue = "true", matchIfMissing = true)
class RedisStreamOutboxMessageQueue implements OutboxMessageQueue {

    private final StringRedisTemplate redisTemplate;

    RedisStreamOutboxMessageQueue(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publish(Long outboxId) {
        redisTemplate.opsForStream().add(StreamRecords.string(
                Map.of(OutboxStream.OUTBOX_ID_FIELD, outboxId.toString())).withStreamKey(OutboxStream.KEY));
    }
}
