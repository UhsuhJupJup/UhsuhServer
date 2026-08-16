package uhsuhjupjup.backend.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

class TwoLevelCacheTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private RedisSerializer<Object> listLongSerializer() {
        var type = mapper.getTypeFactory().constructCollectionType(List.class, Long.class);
        return (RedisSerializer<Object>) (RedisSerializer<?>) new Jackson2JsonRedisSerializer<>(mapper, type);
    }

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, byte[]> fakeRedis(Map<String, byte[]> store) {
        RedisTemplate<String, byte[]> redis = mock(RedisTemplate.class);
        ValueOperations<String, byte[]> ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);
        given(ops.get(anyString())).willAnswer(inv -> store.get(inv.<String>getArgument(0)));
        willAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).given(ops).set(anyString(), any(byte[].class), any(Duration.class));
        given(redis.delete(anyString())).willAnswer(inv -> store.remove(inv.<String>getArgument(0)) != null);
        return redis;
    }

    private TwoLevelCache newCache(String name, RedisTemplate<String, byte[]> redis) {
        return newCache(name, redis, CacheEvictBroadcaster.NOOP);
    }

    private TwoLevelCache newCache(String name, RedisTemplate<String, byte[]> redis, CacheEvictBroadcaster broadcaster) {
        return new TwoLevelCache(
                name,
                Caffeine.newBuilder().maximumSize(100).build(),
                redis,
                listLongSerializer(),
                Duration.ofMinutes(10),
                "test:" + name + "::",
                broadcaster,
                null);
    }

    @Test
    void loader는_miss시_한번만_호출되고_L1과_L2에_채워진다() {
        Map<String, byte[]> store = new HashMap<>();
        TwoLevelCache cache = newCache("c", fakeRedis(store));
        AtomicInteger loads = new AtomicInteger();

        List<Long> first = cache.get(1L, () -> {
            loads.incrementAndGet();
            return List.of(10L, 20L);
        });
        List<Long> second = cache.get(1L, () -> {
            loads.incrementAndGet();
            return List.of(99L);
        });

        assertThat(first).containsExactly(10L, 20L);
        assertThat(second).containsExactly(10L, 20L);
        assertThat(loads.get()).isEqualTo(1);
        assertThat(store).containsKey("test:c::1");
    }

    @Test
    void 다른_인스턴스는_공유_L2에서_같은값을_보고_재계산하지_않는다() {
        Map<String, byte[]> sharedL2 = new HashMap<>();
        TwoLevelCache appA = newCache("c", fakeRedis(sharedL2));
        TwoLevelCache appB = newCache("c", fakeRedis(sharedL2));
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
    void evict는_L1과_L2를_모두_비운다() {
        Map<String, byte[]> store = new HashMap<>();
        TwoLevelCache cache = newCache("c", fakeRedis(store));
        cache.get(1L, () -> List.of(5L));
        assertThat(store).containsKey("test:c::1");

        cache.evict(1L);

        assertThat(store).doesNotContainKey("test:c::1");
        AtomicInteger reloads = new AtomicInteger();
        cache.get(1L, () -> {
            reloads.incrementAndGet();
            return List.of(6L);
        });
        assertThat(reloads.get()).isEqualTo(1);
    }

    @Test
    void L2가_죽어도_원본으로_폴백한다() {
        RedisTemplate<String, byte[]> broken = mock(RedisTemplate.class);
        given(broken.opsForValue()).willThrow(new RuntimeException("redis down"));
        TwoLevelCache cache = newCache("c", broken);

        List<Long> value = cache.get(1L, () -> List.of(1L, 2L));

        assertThat(value).containsExactly(1L, 2L);
    }

    @Test
    void evict시_다른_인스턴스로_방송한다() {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        TwoLevelCache cache = newCache("c", fakeRedis(new HashMap<>()), broadcaster);
        cache.get(1L, () -> List.of(5L));

        cache.evict(1L);
        cache.clear();

        assertThat(broadcaster.evicted).containsExactly("c:1");
        assertThat(broadcaster.cleared).isEqualTo(1);
    }

    @Test
    void evictLocal은_L1만_비우고_L2는_유지한다() {
        Map<String, byte[]> store = new HashMap<>();
        TwoLevelCache cache = newCache("c", fakeRedis(store));
        cache.get(1L, () -> List.of(5L));
        assertThat(store).containsKey("test:c::1");

        cache.evictLocal("1");

        assertThat(store).containsKey("test:c::1");
        AtomicInteger loads = new AtomicInteger();
        List<Long> value = cache.get(1L, () -> {
            loads.incrementAndGet();
            return List.of(9L);
        });
        assertThat(value).containsExactly(5L);
        assertThat(loads.get()).isZero();
    }

    @Test
    void put시_다른_인스턴스로_방송한다() {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        TwoLevelCache cache = newCache("c", fakeRedis(new HashMap<>()), broadcaster);

        cache.put(1L, List.of(5L));

        assertThat(broadcaster.evicted).containsExactly("c:1");
    }

    private static final class RecordingBroadcaster implements CacheEvictBroadcaster {
        private final List<String> evicted = new ArrayList<>();
        private int cleared = 0;

        @Override
        public void broadcastEvict(String cacheName, Object key) {
            evicted.add(cacheName + ":" + key);
        }

        @Override
        public void broadcastClear(String cacheName) {
            cleared++;
        }
    }
}
