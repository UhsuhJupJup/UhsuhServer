package uhsuhjupjup.backend.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
public class CacheInvalidationPublisher {

    public static final String CHANNEL = "uhsuh:cache:invalidation";
    static final String SEPARATOR = "::";

    private final StringRedisTemplate redis;

    public CacheInvalidationPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publishInvalidation(String cacheName, Object key) {
        try {
            redis.convertAndSend(CHANNEL, cacheName + SEPARATOR + key);
        } catch (RuntimeException e) {
            log.warn("로컬 캐시 무효화 메시지 발행 실패, 로컬 캐시 TTL로 맞춰집니다 cache={} key={} cause={}",
                    cacheName, key, e.toString());
        }
    }
}
