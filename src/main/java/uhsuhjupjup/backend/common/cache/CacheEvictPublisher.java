package uhsuhjupjup.backend.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Profile("prod")
public class CacheEvictPublisher implements CacheEvictBroadcaster {

    public static final String CHANNEL = "uhsuh:cache:evict";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String senderId = UUID.randomUUID().toString();

    public CacheEvictPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String senderId() {
        return senderId;
    }

    @Override
    public void broadcastEvict(String cacheName, Object key) {
        publish(new CacheEvictMessage(cacheName, String.valueOf(key), senderId));
    }

    @Override
    public void broadcastClear(String cacheName) {
        publish(new CacheEvictMessage(cacheName, null, senderId));
    }

    private void publish(CacheEvictMessage message) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("캐시 무효화 방송 실패 cache={} key={}", message.cacheName(), message.key(), e);
        }
    }
}
