package uhsuhjupjup.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String KEYWORD_GRAPH = "keywordGraph";

    @Bean
    public CacheManager cacheManager() {
        Cache keywordGraph = new CaffeineCache(
                KEYWORD_GRAPH,
                Caffeine.newBuilder()
                        .maximumSize(2_000)
                        .expireAfterWrite(Duration.ofHours(1))
                        .recordStats()
                        .build());

        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(keywordGraph));
        return cacheManager;
    }
}
