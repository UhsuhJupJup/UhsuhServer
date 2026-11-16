package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
public class CacheInvalidationPublisher {

    public static final String CHANNEL = "uhsuh:cache:invalidation";
    static final String SEPARATOR = "::";

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;

    public CacheInvalidationPublisher(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
    }

    public void register(String cacheName) {
        published(cacheName, "success");
        published(cacheName, "failure");
    }

    public void publishInvalidation(String cacheName, Object key) {
        try {
            Long receivers = redis.convertAndSend(CHANNEL, cacheName + SEPARATOR + key);
            published(cacheName, "success").increment();
            log.info("로컬 캐시 무효화 발행 cache={} key={} 구독_연결={}", cacheName, key, receivers);
        } catch (RuntimeException e) {
            published(cacheName, "failure").increment();
            log.warn("로컬 캐시 무효화 메시지 발행 실패, 로컬 캐시 TTL로 맞춰집니다 cache={} key={} cause={}",
                    cacheName, key, e.toString());
        }
    }

    private Counter published(String cacheName, String result) {
        return Counter.builder("uhsuh.cache.invalidation.published").tag("cache", cacheName).tag("result", result)
                .register(meterRegistry);
    }
}
