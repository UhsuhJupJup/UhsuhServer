package uhsuhjupjup.backend.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 실제 Redis(Testcontainers)에 두 개의 TwoLevelCacheManager(= 두 인스턴스)를 붙여
 * 공유 L2와 Pub/Sub 무효화 전파를 검증한다.
 */
@Testcontainers
class TwoLevelCachePropagationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory cf;

    private final ObjectMapper mapper = new ObjectMapper();
    private final RedisSerializer<Object> serializer = listLongSerializer();
    private final List<RedisMessageListenerContainer> containers = new ArrayList<>();

    @BeforeAll
    static void startRedis() {
        cf = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        cf.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        cf.destroy();
    }

    @AfterEach
    void cleanup() {
        containers.forEach(RedisMessageListenerContainer::stop);
        containers.clear();
        RedisConnection connection = cf.getConnection();
        try {
            connection.serverCommands().flushAll();
        } finally {
            connection.close();
        }
    }

    @Test
    void 공유_L2로_다른_인스턴스는_재계산하지_않는다() {
        RedisTemplate<String, byte[]> sharedL2 = sharedL2();
        TwoLevelCache appA = newInstance("c", sharedL2);
        TwoLevelCache appB = newInstance("c", sharedL2);
        AtomicInteger bLoads = new AtomicInteger();

        appA.get(1L, () -> List.of(7L));
        List<Long> fromB = appB.get(1L, () -> {
            bLoads.incrementAndGet();
            return List.of(0L);
        });

        assertThat(fromB).containsExactly(7L);
        assertThat(bLoads.get()).isZero();
    }

    @Test
    void evict가_다른_인스턴스의_L1을_비운다() {
        RedisTemplate<String, byte[]> sharedL2 = sharedL2();
        TwoLevelCache appA = newInstance("c", sharedL2);
        TwoLevelCache appB = newInstance("c", sharedL2);

        appA.get(1L, () -> List.of(7L));
        appB.get(1L, () -> List.of(7L));
        assertThat(l1(appB).getIfPresent("1")).isNotNull();

        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            appA.evict(1L);
            assertThat(l1(appB).getIfPresent("1")).isNull();
        });
    }

    @Test
    void clear가_다른_인스턴스의_L1_전체를_비운다() {
        RedisTemplate<String, byte[]> sharedL2 = sharedL2();
        TwoLevelCache appA = newInstance("c", sharedL2);
        TwoLevelCache appB = newInstance("c", sharedL2);

        appA.get(1L, () -> List.of(7L));
        appB.get(1L, () -> List.of(7L));
        appB.get(2L, () -> List.of(8L));
        assertThat(l1(appB).getIfPresent("1")).isNotNull();
        assertThat(l1(appB).getIfPresent("2")).isNotNull();

        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            appA.clear();
            assertThat(l1(appB).getIfPresent("1")).isNull();
            assertThat(l1(appB).getIfPresent("2")).isNull();
        });
    }

    private TwoLevelCache newInstance(String cacheName, RedisTemplate<String, byte[]> sharedL2) {
        StringRedisTemplate stringRedis = new StringRedisTemplate(cf);
        CacheEvictPublisher publisher = new CacheEvictPublisher(stringRedis, mapper);
        TwoLevelCache cache = new TwoLevelCache(
                cacheName,
                Caffeine.newBuilder().maximumSize(100).build(),
                sharedL2,
                serializer,
                Duration.ofMinutes(10),
                "test:" + cacheName + "::",
                publisher,
                null);
        TwoLevelCacheManager manager = new TwoLevelCacheManager(List.of(cache));
        CacheEvictSubscriber subscriber = new CacheEvictSubscriber(manager, mapper, publisher);
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        container.afterPropertiesSet();
        container.addMessageListener(subscriber, new ChannelTopic(CacheEvictPublisher.CHANNEL));
        container.start();
        containers.add(container);
        return cache;
    }

    private RedisTemplate<String, byte[]> sharedL2() {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    @SuppressWarnings("unchecked")
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> l1(TwoLevelCache cache) {
        return (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
    }

    @SuppressWarnings("unchecked")
    private RedisSerializer<Object> listLongSerializer() {
        var type = mapper.getTypeFactory().constructCollectionType(List.class, Long.class);
        return (RedisSerializer<Object>) (RedisSerializer<?>) new Jackson2JsonRedisSerializer<>(mapper, type);
    }
}
