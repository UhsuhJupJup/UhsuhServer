package uhsuhjupjup.backend.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import uhsuhjupjup.backend.common.cache.CacheLockSettings;
import uhsuhjupjup.backend.common.cache.DistributedSingleFlightCache;
import uhsuhjupjup.backend.common.cache.TtlJitter;
import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String KEYWORD_GRAPH = "keywordGraph";
    public static final String KEY_PREFIX = "uhsuh:cache:";
    public static final String LOCK_PREFIX = "uhsuh:lock:";
    public static final Duration KEYWORD_GRAPH_MAX_TTL = Duration.ofHours(1);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     ObjectProvider<MeterRegistry> meterRegistry,
                                     @Value("${cache.keyword-graph.ttl-min:55m}") Duration ttlMin,
                                     @Value("${cache.keyword-graph.ttl-max:60m}") Duration ttlMax,
                                     @Value("${cache.keyword-graph.lock-ttl:10s}") Duration lockTtl,
                                     @Value("${cache.keyword-graph.lock-wait-timeout:3s}") Duration lockWaitTimeout,
                                     @Value("${cache.keyword-graph.lock-retry-interval:50ms}") Duration lockRetryInterval,
                                     @Value("${cache.keyword-graph.redis-failure-backoff:10s}") Duration redisFailureBackoff) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(keywordGraphCache(connectionFactory,
                meterRegistry.getIfAvailable(SimpleMeterRegistry::new),
                keywordGraphTtl(ttlMin, ttlMax),
                new CacheLockSettings(lockTtl, lockWaitTimeout, lockRetryInterval, redisFailureBackoff))));
        return cacheManager;
    }

    public static DistributedSingleFlightCache keywordGraphCache(RedisConnectionFactory connectionFactory,
                                                                 MeterRegistry meterRegistry,
                                                                 TtlJitter ttl,
                                                                 CacheLockSettings lockSettings) {
        return new DistributedSingleFlightCache(KEYWORD_GRAPH, KEY_PREFIX, LOCK_PREFIX,
                DistributedSingleFlightCache.bytesTemplate(connectionFactory),
                keywordGraphSerializer(), lockSettings, ttl, meterRegistry);
    }

    public static TtlJitter keywordGraphTtl(Duration min, Duration max) {
        if (max.compareTo(KEYWORD_GRAPH_MAX_TTL) > 0) {
            throw new IllegalStateException("키워드 그래프 캐시 TTL 상한은 1시간을 넘을 수 없습니다: " + max);
        }
        return new TtlJitter(min, max);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(CacheConfig.class.getName(), false);
    }

    @SuppressWarnings("unchecked")
    private static RedisSerializer<Object> keywordGraphSerializer() {
        return (RedisSerializer<Object>) (RedisSerializer<?>) new Jackson2JsonRedisSerializer<>(KeywordGraphResult.class);
    }
}
