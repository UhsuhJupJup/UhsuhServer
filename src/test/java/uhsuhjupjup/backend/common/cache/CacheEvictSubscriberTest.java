package uhsuhjupjup.backend.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

class CacheEvictSubscriberTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RedisSerializer<Object> serializer = listLongSerializer();

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

    private TwoLevelCache cache(String name, Map<String, byte[]> store) {
        return new TwoLevelCache(name, Caffeine.newBuilder().maximumSize(100).build(),
                fakeRedis(store), serializer, Duration.ofMinutes(10), "test:" + name + "::",
                CacheEvictBroadcaster.NOOP, null);
    }

    private DefaultMessage message(String cacheName, String key, String senderId) throws Exception {
        String json = mapper.writeValueAsString(new CacheEvictMessage(cacheName, key, senderId));
        return new DefaultMessage(CacheEvictPublisher.CHANNEL.getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));
    }

    private CacheEvictPublisher publisher() {
        return new CacheEvictPublisher(mock(StringRedisTemplate.class), mapper);
    }

    private TwoLevelCacheManager managerOf(TwoLevelCache cache) {
        return new TwoLevelCacheManager(List.of(cache));
    }

    @Test
    void 다른_인스턴스의_evict_메시지는_로컬_L1을_비운다() throws Exception {
        Map<String, byte[]> store = new HashMap<>();
        TwoLevelCache cache = cache("c", store);
        cache.get(1L, () -> List.of(5L));
        store.put("test:c::1", serializer.serialize(List.of(99L)));

        CacheEvictPublisher pub = publisher();
        CacheEvictSubscriber subscriber = new CacheEvictSubscriber(managerOf(cache), mapper, pub);
        subscriber.onMessage(message("c", "1", "other-instance"), null);

        AtomicInteger loads = new AtomicInteger();
        List<Long> after = cache.get(1L, () -> {
            loads.incrementAndGet();
            return List.of(0L);
        });
        assertThat(after).containsExactly(99L);
        assertThat(loads.get()).isZero();
    }

    @Test
    void 자기가_보낸_메시지는_무시한다() throws Exception {
        Map<String, byte[]> store = new HashMap<>();
        TwoLevelCache cache = cache("c", store);
        cache.get(1L, () -> List.of(5L));
        store.put("test:c::1", serializer.serialize(List.of(99L)));

        CacheEvictPublisher pub = publisher();
        CacheEvictSubscriber subscriber = new CacheEvictSubscriber(managerOf(cache), mapper, pub);
        subscriber.onMessage(message("c", "1", pub.senderId()), null);

        List<Long> after = cache.get(1L, () -> List.of(0L));
        assertThat(after).containsExactly(5L);
    }

    @Test
    void clear_메시지는_로컬_L1_전체를_비운다() throws Exception {
        Map<String, byte[]> store = new HashMap<>();
        TwoLevelCache cache = cache("c", store);
        cache.get(1L, () -> List.of(5L));
        store.put("test:c::1", serializer.serialize(List.of(99L)));

        CacheEvictPublisher pub = publisher();
        CacheEvictSubscriber subscriber = new CacheEvictSubscriber(managerOf(cache), mapper, pub);
        subscriber.onMessage(message("c", null, "other-instance"), null);

        List<Long> after = cache.get(1L, () -> List.of(0L));
        assertThat(after).containsExactly(99L);
    }
}
