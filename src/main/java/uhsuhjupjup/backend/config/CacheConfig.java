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

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(KEYWORD_ARTICLES);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats());
        return cacheManager;
    }
}
