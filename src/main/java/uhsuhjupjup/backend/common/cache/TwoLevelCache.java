package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;

@Slf4j
public class TwoLevelCache implements Cache {

    private final String name;
    private final com.github.benmanes.caffeine.cache.Cache<Object, Object> l1;
    private final RedisTemplate<String, byte[]> l2;
    private final RedisSerializer<Object> valueSerializer;
    private final Duration l2Ttl;
    private final String keyPrefix;
    private final CacheEvictBroadcaster broadcaster;
    private final MeterRegistry meterRegistry;

    public TwoLevelCache(String name,
                         com.github.benmanes.caffeine.cache.Cache<Object, Object> l1,
                         RedisTemplate<String, byte[]> l2,
                         RedisSerializer<Object> valueSerializer,
                         Duration l2Ttl,
                         String keyPrefix,
                         CacheEvictBroadcaster broadcaster,
                         MeterRegistry meterRegistry) {
        this.name = name;
        this.l1 = l1;
        this.l2 = l2;
        this.valueSerializer = valueSerializer;
        this.l2Ttl = l2Ttl;
        this.keyPrefix = keyPrefix;
        this.broadcaster = broadcaster;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return l1;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = lookup(key);
        return value == null ? null : new SimpleValueWrapper(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        Object value = lookup(key);
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException(
                    "캐시 값 타입 불일치 cache=%s key=%s expected=%s actual=%s"
                            .formatted(name, key, type.getName(), value.getClass().getName()));
        }
        return type == null ? (T) value : type.cast(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object cached = lookup(key);
        if (cached != null) {
            return (T) cached;
        }
        return (T) l1.get(cacheKey(key), ignored -> loadThroughL2(key, valueLoader));
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            return;
        }
        l1.put(cacheKey(key), value);
        writeL2(key, value);
        broadcast(() -> broadcaster.broadcastEvict(name, key));
    }

    @Override
    public void evict(Object key) {
        l1.invalidate(cacheKey(key));
        l2Delete(key);
        broadcast(() -> broadcaster.broadcastEvict(name, key));
    }

    @Override
    public void clear() {
        l1.invalidateAll();
        l2Clear();
        broadcast(() -> broadcaster.broadcastClear(name));
    }

    public void evictLocal(String key) {
        l1.invalidate(key);
    }

    public void evictLocalAll() {
        l1.invalidateAll();
    }

    private Object lookup(Object key) {
        Object l1Value = l1.getIfPresent(cacheKey(key));
        if (l1Value != null) {
            recordGet("hit", "l1");
            return l1Value;
        }
        byte[] bytes = l2Get(key);
        if (bytes == null) {
            return null;
        }
        Object value = valueSerializer.deserialize(bytes);
        if (value == null) {
            return null;
        }
        l1.put(cacheKey(key), value);
        recordGet("hit", "l2");
        return value;
    }

    private Object loadThroughL2(Object key, Callable<?> valueLoader) {
        byte[] bytes = l2Get(key);
        if (bytes != null) {
            Object value = valueSerializer.deserialize(bytes);
            if (value != null) {
                recordGet("hit", "l2");
                return value;
            }
        }
        Object loaded;
        try {
            loaded = valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
        if (loaded != null) {
            writeL2(key, loaded);
        }
        recordGet("miss", "origin");
        return loaded;
    }

    private byte[] l2Get(Object key) {
        try {
            return l2.opsForValue().get(redisKey(key));
        } catch (RuntimeException e) {
            log.warn("L2 조회 실패, L1/원본 폴백 cache={} key={}", name, key, e);
            return null;
        }
    }

    private void writeL2(Object key, Object value) {
        try {
            byte[] bytes = valueSerializer.serialize(value);
            if (l2Ttl != null && !l2Ttl.isZero() && !l2Ttl.isNegative()) {
                l2.opsForValue().set(redisKey(key), bytes, l2Ttl);
            } else {
                l2.opsForValue().set(redisKey(key), bytes);
            }
        } catch (RuntimeException e) {
            log.warn("L2 저장 실패 cache={} key={}", name, key, e);
        }
    }

    private void l2Delete(Object key) {
        try {
            l2.delete(redisKey(key));
        } catch (RuntimeException e) {
            log.warn("L2 삭제 실패 cache={} key={}", name, key, e);
        }
    }

    private void l2Clear() {
        try {
            Set<String> keys = l2.keys(keyPrefix + "*");
            if (keys != null && !keys.isEmpty()) {
                l2.delete(keys);
            }
        } catch (RuntimeException e) {
            log.warn("L2 clear 실패 cache={}", name, e);
        }
    }

    private void broadcast(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("캐시 무효화 방송 실패 cache={}", name, e);
        }
    }

    private void recordGet(String result, String tier) {
        if (meterRegistry == null) {
            return;
        }
        try {
            meterRegistry.counter("cache.gets", "cache", name, "result", result, "tier", tier).increment();
        } catch (RuntimeException e) {
            log.debug("메트릭 기록 실패 cache={}", name, e);
        }
    }

    private String cacheKey(Object key) {
        return String.valueOf(key);
    }

    private String redisKey(Object key) {
        return keyPrefix + cacheKey(key);
    }
}
