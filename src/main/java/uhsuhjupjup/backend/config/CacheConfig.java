package uhsuhjupjup.backend.config;

import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import uhsuhjupjup.backend.common.cache.CacheInvalidationListener;
import uhsuhjupjup.backend.common.cache.CacheInvalidationPublisher;
import uhsuhjupjup.backend.common.cache.CacheLockSettings;
import uhsuhjupjup.backend.common.cache.LocalCache;
import uhsuhjupjup.backend.common.cache.LocalCacheSettings;
import uhsuhjupjup.backend.common.cache.RedisCacheLayer;
import uhsuhjupjup.backend.common.cache.TieredCache;
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
    public static final Duration KEYWORD_GRAPH_LOCAL_MAX_TTL = Duration.ofMinutes(5);

    @Bean
    public TieredCache keywordGraphCache(RedisConnectionFactory connectionFactory,
                                         ObjectProvider<MeterRegistry> meterRegistry,
                                         @Value("${cache.keyword-graph.ttl-min:55m}") Duration ttlMin,
                                         @Value("${cache.keyword-graph.ttl-max:60m}") Duration ttlMax,
                                         @Value("${cache.keyword-graph.local-ttl-min:270s}") Duration localTtlMin,
                                         @Value("${cache.keyword-graph.local-ttl-max:5m}") Duration localTtlMax,
                                         @Value("${cache.keyword-graph.local-max-size:1000}") long localMaxSize,
                                         @Value("${cache.keyword-graph.lock-ttl:10s}") Duration lockTtl,
                                         @Value("${cache.keyword-graph.lock-wait-timeout:3s}") Duration lockWaitTimeout,
                                         @Value("${cache.keyword-graph.lock-retry-interval:50ms}") Duration lockRetryInterval,
                                         @Value("${cache.keyword-graph.redis-failure-backoff:10s}") Duration redisFailureBackoff) {
        TtlJitter remoteTtl = keywordGraphTtl(ttlMin, ttlMax);
        return keywordGraphCache(connectionFactory, meterRegistry.getIfAvailable(SimpleMeterRegistry::new),
                remoteTtl,
                keywordGraphLocalSettings(localTtlMin, localTtlMax, localMaxSize, remoteTtl),
                new CacheLockSettings(lockTtl, lockWaitTimeout, lockRetryInterval, redisFailureBackoff),
                Ticker.systemTicker());
    }

    @Bean
    public CacheManager cacheManager(TieredCache keywordGraphCache) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(keywordGraphCache));
        return cacheManager;
    }

    @Bean
    @ConditionalOnProperty(name = "cache.invalidation.listener-enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(RedisConnectionFactory connectionFactory,
                                                                            List<TieredCache> caches,
                                                                            ObjectProvider<MeterRegistry> meterRegistry) {
        return invalidationListenerContainer(connectionFactory, caches, meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }

    public static TieredCache keywordGraphCache(RedisConnectionFactory connectionFactory,
                                                MeterRegistry meterRegistry,
                                                TtlJitter remoteTtl,
                                                LocalCacheSettings localSettings,
                                                CacheLockSettings lockSettings,
                                                Ticker ticker) {
        RedisCacheLayer remote = new RedisCacheLayer(KEYWORD_GRAPH, KEY_PREFIX, LOCK_PREFIX,
                RedisCacheLayer.bytesTemplate(connectionFactory), keywordGraphSerializer(), lockSettings, remoteTtl,
                meterRegistry);
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        return new TieredCache(KEYWORD_GRAPH, new LocalCache(localSettings, ticker), remote, lockSettings,
                new CacheInvalidationPublisher(redis, meterRegistry), Long::valueOf, meterRegistry);
    }

    public static RedisMessageListenerContainer invalidationListenerContainer(RedisConnectionFactory connectionFactory,
                                                                              List<TieredCache> caches,
                                                                              MeterRegistry meterRegistry) {
        CacheInvalidationListener listener = new CacheInvalidationListener(caches, meterRegistry);
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(CacheInvalidationPublisher.CHANNEL));
        return container;
    }

    public static TtlJitter keywordGraphTtl(Duration min, Duration max) {
        if (max.compareTo(KEYWORD_GRAPH_MAX_TTL) > 0) {
            throw new IllegalStateException("키워드 그래프 캐시 TTL 상한은 1시간을 넘을 수 없습니다: " + max);
        }
        return new TtlJitter(min, max);
    }

    public static LocalCacheSettings keywordGraphLocalSettings(Duration min, Duration max, long maximumSize,
                                                               TtlJitter remoteTtl) {
        if (max.compareTo(KEYWORD_GRAPH_LOCAL_MAX_TTL) > 0) {
            throw new IllegalStateException("키워드 그래프 로컬 캐시 TTL 상한은 5분을 넘을 수 없습니다: " + max);
        }
        if (max.compareTo(remoteTtl.min()) >= 0) {
            throw new IllegalStateException("로컬 캐시 TTL은 Redis TTL보다 짧아야 합니다: " + max + " >= " + remoteTtl.min());
        }
        return new LocalCacheSettings(new TtlJitter(min, max), maximumSize);
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
