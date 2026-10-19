package uhsuhjupjup.backend.keyword;

import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.common.cache.CacheInvalidationPublisher;
import uhsuhjupjup.backend.common.cache.CacheLockSettings;
import uhsuhjupjup.backend.common.cache.LocalCacheSettings;
import uhsuhjupjup.backend.common.cache.TieredCache;
import uhsuhjupjup.backend.config.CacheConfig;
import uhsuhjupjup.backend.keyword.application.KeywordGraphProvider;
import uhsuhjupjup.backend.keyword.application.KeywordService;
import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.pipeline.collection.application.FeedClient;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.application.MatchingService;
import uhsuhjupjup.backend.pipeline.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.support.RedisTestConfiguration;
import uhsuhjupjup.backend.support.SharedTestContainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = "cache.invalidation.listener-enabled=true")
@ActiveProfiles("test")
@Import(RedisTestConfiguration.class)
class KeywordGraphRefreshIntegrationTest {

    private static final GenericContainer<?> REDIS = SharedTestContainers.redis();

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleKeywordRepository articleKeywordRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private KeywordGraphProvider keywordGraphProvider;

    @Autowired
    private KeywordService keywordService;

    @Autowired
    private TieredCache keywordGraphCache;

    @Autowired
    private MatchingService matchingService;

    @MockitoBean
    private FeedClient feedClient;

    @MockitoBean
    private KeywordClassifier keywordClassifier;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private final List<LettuceConnectionFactory> factories = new ArrayList<>();
    private final List<RedisMessageListenerContainer> containers = new ArrayList<>();
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        redis = new StringRedisTemplate(connectionFactory());
        redis.afterPropertiesSet();
        Set<String> keys = redis.keys("uhsuh:*:keywordGraph::*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        keywordGraphCache.clearLocal();
    }

    @AfterEach
    void tearDown() {
        containers.forEach(container -> {
            try {
                container.stop();
                container.destroy();
            } catch (Exception ignored) {
            }
        });
        factories.forEach(LettuceConnectionFactory::destroy);
    }

    @Test
    void 새_글_15개가_매칭되어_커밋되면_Redis가_115개로_갱신되고_두_서버의_로컬_캐시가_지워진다() throws Exception {
        Blog blog = blogRepository.save(Blog.create("기술 블로그", "tech.example.com", "https://tech.example.com/rss"));
        Keyword performance = keywordRepository.save(Keyword.create("성능 최적화"));
        Keyword spring = keywordRepository.save(Keyword.create("Spring"));
        for (int i = 0; i < 100; i++) {
            Article article = Article.create(blog, "이전 글 " + i, "https://tech.example.com/old/" + i, LocalDateTime.now().minusDays(10));
            article.markClassified(LocalDateTime.now().minusDays(9));
            articleRepository.save(article);
            articleKeywordRepository.save(ArticleKeyword.of(article, performance, "title"));
            if (i < 5) {
                articleKeywordRepository.save(ArticleKeyword.of(article, spring, "title"));
            }
        }
        Long performanceId = performance.getId();
        Long springId = spring.getId();

        TieredCache secondServer = secondServer();
        RedisMessageListenerContainer probe = probe();
        awaitSubscribers(3);

        KeywordGraphResult firstServerBefore = keywordGraphProvider.keywordGraph(performanceId);
        KeywordGraphResult secondServerBefore = secondServer.get(performanceId, () -> keywordService.getGraph(performanceId));
        assertThat(firstServerBefore.neighborhood().articleCount()).isEqualTo(100);
        assertThat(secondServerBefore.neighborhood().articleCount()).isEqualTo(100);
        assertThat(keywordGraphCache.localValue(performanceId)).isNotNull();
        assertThat(secondServer.localValue(performanceId)).isNotNull();
        Thread.sleep(300);
        assertThat(messages).isEmpty();

        for (int i = 0; i < 15; i++) {
            articleRepository.save(Article.create(blog, "새 글 " + i, "https://tech.example.com/new/" + i, LocalDateTime.now()));
        }
        given(feedClient.fetch(anyString())).willReturn(List.of());
        given(keywordClassifier.classify(any(), any(), any())).willAnswer(invocation -> {
            String title = invocation.getArgument(0);
            if (!title.startsWith("새 글")) {
                return List.of();
            }
            int number = Integer.parseInt(title.substring("새 글 ".length()));
            return number < 3
                    ? List.of(new KeywordMatch(performanceId, "title"), new KeywordMatch(springId, "title"))
                    : List.of(new KeywordMatch(performanceId, "title"));
        });

        MatchingResult result = matchingService.matchRecent();

        assertThat(result.articlesTagged()).isEqualTo(15);
        assertThat(redis.opsForValue().get("uhsuh:cache:keywordGraph::" + performanceId)).contains("\"articleCount\":115");
        assertThat(redis.opsForValue().get("uhsuh:cache:keywordGraph::" + springId)).contains("\"articleCount\":8");
        awaitMessages(2);
        assertThat(messages).containsExactlyInAnyOrder("keywordGraph::" + performanceId, "keywordGraph::" + springId);
        awaitLocalEmpty(keywordGraphCache, performanceId);
        awaitLocalEmpty(secondServer, performanceId);

        KeywordGraphResult firstServerAfter = keywordGraphProvider.keywordGraph(performanceId);
        KeywordGraphResult secondServerAfter = secondServer.get(performanceId, () -> {
            throw new IllegalStateException("서버 2는 Redis의 새 값을 읽어야 합니다");
        });
        assertThat(firstServerAfter.neighborhood().articleCount()).isEqualTo(115);
        assertThat(secondServerAfter.neighborhood().articleCount()).isEqualTo(115);
        assertThat(((KeywordGraphResult) keywordGraphCache.localValue(performanceId)).neighborhood().articleCount()).isEqualTo(115);
        assertThat(((KeywordGraphResult) secondServer.localValue(performanceId)).neighborhood().articleCount()).isEqualTo(115);

        MatchingResult nothingNew = matchingService.matchRecent();
        Thread.sleep(300);
        assertThat(nothingNew.articlesTagged()).isZero();
        assertThat(messages).hasSize(2);
        probe.stop();
    }

    private TieredCache secondServer() {
        LettuceConnectionFactory factory = connectionFactory();
        TieredCache cache = CacheConfig.keywordGraphCache(factory, new SimpleMeterRegistry(),
                CacheConfig.keywordGraphTtl(Duration.ofMinutes(55), Duration.ofMinutes(60)),
                new LocalCacheSettings(Duration.ofMinutes(5), 1_000),
                new CacheLockSettings(Duration.ofSeconds(10), Duration.ofSeconds(3), Duration.ofMillis(50), Duration.ofSeconds(10)),
                Ticker.systemTicker());
        RedisMessageListenerContainer container = CacheConfig.invalidationListenerContainer(factory, List.of(cache));
        container.afterPropertiesSet();
        container.start();
        containers.add(container);
        return cache;
    }

    private RedisMessageListenerContainer probe() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory());
        container.addMessageListener((message, pattern) -> messages.add(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(CacheInvalidationPublisher.CHANNEL));
        container.afterPropertiesSet();
        container.start();
        containers.add(container);
        return container;
    }

    private LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factories.add(factory);
        return factory;
    }

    @SuppressWarnings("unchecked")
    private void awaitSubscribers(long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        long count = 0;
        while (System.nanoTime() < deadline) {
            count = redis.execute((RedisCallback<Long>) connection -> {
                var commands = (io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
                try {
                    return commands.pubsubNumsub(CacheInvalidationPublisher.CHANNEL.getBytes()).get(2, TimeUnit.SECONDS)
                            .values().stream().mapToLong(Long::longValue).sum();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            if (count >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(count).isGreaterThanOrEqualTo(expected);
    }

    private void awaitMessages(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (messages.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private void awaitLocalEmpty(TieredCache cache, Long key) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (cache.localValue(key) != null && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(cache.localValue(key)).isNull();
    }
}
