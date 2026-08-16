package uhsuhjupjup.backend.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import uhsuhjupjup.backend.common.cache.TwoLevelCache;
import uhsuhjupjup.backend.common.cache.TwoLevelCacheManager;
import uhsuhjupjup.backend.learningnote.application.dto.NoteGraphResult;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String KEYWORD_ARTICLES = "keywordArticles";
    public static final String GLOBAL_GRAPH = "globalGraph";

    private static final ObjectMapper CACHE_MAPPER = new ObjectMapper();

    @Bean
    public CacheManager cacheManager(RedisTemplate<String, byte[]> cacheRedisTemplate) {
        JavaType listOfLong = CACHE_MAPPER.getTypeFactory().constructCollectionType(List.class, Long.class);

        TwoLevelCache globalGraph = new TwoLevelCache(
                GLOBAL_GRAPH,
                Caffeine.newBuilder()
                        .maximumSize(4)
                        .expireAfterWrite(Duration.ofHours(1))
                        .recordStats()
                        .build(),
                cacheRedisTemplate,
                objectSerializer(new Jackson2JsonRedisSerializer<>(CACHE_MAPPER, NoteGraphResult.class)),
                Duration.ofHours(25),
                "uhsuh:cache:globalGraph::");

        TwoLevelCache keywordArticles = new TwoLevelCache(
                KEYWORD_ARTICLES,
                Caffeine.newBuilder()
                        .maximumSize(2_000)
                        .expireAfterWrite(Duration.ofMinutes(10))
                        .recordStats()
                        .build(),
                cacheRedisTemplate,
                objectSerializer(new Jackson2JsonRedisSerializer<>(CACHE_MAPPER, listOfLong)),
                Duration.ofMinutes(10),
                "uhsuh:cache:keywordArticles::");

        return new TwoLevelCacheManager(List.of(globalGraph, keywordArticles));
    }

    @SuppressWarnings("unchecked")
    private static RedisSerializer<Object> objectSerializer(RedisSerializer<?> serializer) {
        return (RedisSerializer<Object>) serializer;
    }
}
