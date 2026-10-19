package uhsuhjupjup.backend.keyword.application;

import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborLink;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborhoodResult;
import uhsuhjupjup.backend.article.application.dto.KeywordRelation;
import uhsuhjupjup.backend.common.cache.CacheLockSettings;
import uhsuhjupjup.backend.common.cache.LocalCacheSettings;
import uhsuhjupjup.backend.common.cache.TieredCache;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.config.CacheConfig;
import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;
import uhsuhjupjup.backend.support.SharedTestContainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = {CacheConfig.class, KeywordGraphProvider.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "cache.invalidation.listener-enabled=false")
@ImportAutoConfiguration(RedisAutoConfiguration.class)
class KeywordGraphProviderCacheTest {

    private static final GenericContainer<?> REDIS = SharedTestContainers.redis();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private KeywordGraphProvider provider;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @MockitoBean
    private KeywordService keywordService;

    @BeforeEach
    void clearCache() {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.KEYWORD_GRAPH)).clear();
    }

    @Test
    void 같은_키워드를_두_번_조회하면_집계는_한_번만_한다() {
        given(keywordService.getGraph(3L)).willReturn(graph(3L, "MySQL"));

        KeywordGraphResult first = provider.keywordGraph(3L);
        KeywordGraphResult second = provider.keywordGraph(3L);

        assertThat(first).isEqualTo(graph(3L, "MySQL"));
        assertThat(second).isEqualTo(first);
        verify(keywordService, times(1)).getGraph(3L);
    }

    @Test
    void 서로_다른_키워드는_따로_담는다() {
        given(keywordService.getGraph(3L)).willReturn(graph(3L, "MySQL"));
        given(keywordService.getGraph(7L)).willReturn(graph(7L, "Kafka"));

        provider.keywordGraph(3L);
        provider.keywordGraph(7L);
        KeywordGraphResult again = provider.keywordGraph(7L);

        assertThat(again.keywordName()).isEqualTo("Kafka");
        verify(keywordService, times(1)).getGraph(3L);
        verify(keywordService, times(1)).getGraph(7L);
    }

    @Test
    void 다른_서버가_담아_둔_값을_계산_없이_그대로_쓴다() {
        LettuceConnectionFactory otherConnection = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        otherConnection.afterPropertiesSet();
        try {
            TieredCache otherServer = CacheConfig.keywordGraphCache(otherConnection,
                    new SimpleMeterRegistry(),
                    CacheConfig.keywordGraphTtl(Duration.ofMinutes(55), Duration.ofMinutes(60)),
                    new LocalCacheSettings(Duration.ofMinutes(5), 1_000),
                    new CacheLockSettings(Duration.ofSeconds(10), Duration.ofSeconds(3), Duration.ofMillis(50), Duration.ofSeconds(10)),
                    Ticker.systemTicker());
            otherServer.put(5L, graph(5L, "Redis"));

            KeywordGraphResult result = provider.keywordGraph(5L);

            assertThat(result).isEqualTo(graph(5L, "Redis"));
            verify(keywordService, never()).getGraph(5L);
        } finally {
            otherConnection.destroy();
        }
    }

    @Test
    void Redis에_JSON으로_55분에서_60분_사이_동안_담는다() {
        given(keywordService.getGraph(3L)).willReturn(graph(3L, "MySQL"));

        provider.keywordGraph(3L);

        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        String key = CacheConfig.KEY_PREFIX + CacheConfig.KEYWORD_GRAPH + "::3";
        assertThat(redis.opsForValue().get(key))
                .contains("\"keywordId\":3")
                .contains("\"keywordName\":\"MySQL\"")
                .contains("\"name\":\"Redis\"");
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(3_290L, 3_600L);
    }

    @Test
    void 같은_키워드에_요청이_동시에_몰려도_집계는_한_번만_한다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        given(keywordService.getGraph(3L)).willAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return graph(3L, "MySQL");
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<KeywordGraphResult>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> provider.keywordGraph(3L)));
            }
            Thread.sleep(300);
            release.countDown();
            for (Future<KeywordGraphResult> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(graph(3L, "MySQL"));
            }
        } finally {
            pool.shutdownNow();
        }

        verify(keywordService, times(1)).getGraph(3L);
    }

    @Test
    void 없는_키워드는_담지_않고_예외를_그대로_던진다() {
        given(keywordService.getGraph(99L)).willThrow(new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));

        assertThatThrownBy(() -> provider.keywordGraph(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KEYWORD_NOT_FOUND));
        assertThatThrownBy(() -> provider.keywordGraph(99L)).isInstanceOf(BusinessException.class);

        verify(keywordService, times(2)).getGraph(99L);
    }

    private KeywordGraphResult graph(Long keywordId, String name) {
        return new KeywordGraphResult(keywordId, name, new KeywordNeighborhoodResult(
                5,
                List.of(new KeywordRelation(11L, "Redis", 2L), new KeywordRelation(12L, "Spring", 1L)),
                List.of(new KeywordNeighborLink(11L, 12L, 1L))));
    }
}
