package uhsuhjupjup.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String KEYWORD_ARTICLES = "keywordArticles";
    public static final String GLOBAL_GRAPH = "globalGraph";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(KEYWORD_ARTICLES, Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build());
        cacheManager.registerCustomCache(GLOBAL_GRAPH, Caffeine.newBuilder()
                .maximumSize(4)
                .recordStats()
                .build());
        return cacheManager;
    }
}
