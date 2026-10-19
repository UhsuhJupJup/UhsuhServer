package uhsuhjupjup.backend.config;

import org.springframework.boot.actuate.metrics.cache.CacheMeterBinderProvider;
import org.springframework.boot.actuate.metrics.cache.RedisCacheMetrics;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import uhsuhjupjup.backend.common.cache.SingleFlightCache;
import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String KEYWORD_GRAPH = "keywordGraph";
    public static final String KEY_PREFIX = "uhsuh:cache:";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration keywordGraph = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(KeywordGraphResult.class)));

        RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(KEYWORD_GRAPH, keywordGraph)
                .disableCreateOnMissingCache()
                .enableStatistics()
                .build();
        redisCacheManager.afterPropertiesSet();

        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                new SingleFlightCache(Objects.requireNonNull(redisCacheManager.getCache(KEYWORD_GRAPH)))));
        return cacheManager;
    }

    @Bean
    public CacheMeterBinderProvider<SingleFlightCache> singleFlightCacheMeterBinderProvider() {
        return (cache, tags) -> cache.getDelegate() instanceof RedisCache redisCache
                ? new RedisCacheMetrics(redisCache, tags)
                : null;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(CacheConfig.class.getName(), false);
    }
}
