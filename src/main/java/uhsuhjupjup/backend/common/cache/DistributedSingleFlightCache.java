package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class DistributedSingleFlightCache implements Cache {

    private static final RedisScript<Long> STORE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('set', KEYS[2], ARGV[2], 'PX', ARGV[3]) return 1 end return 0",
            Long.class);
    private static final RedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0",
            Long.class);

    private final String name;
    private final String cacheKeyPrefix;
    private final String lockKeyPrefix;
    private final RedisTemplate<String, byte[]> redis;
    private final RedisSerializer<Object> serializer;
    private final CacheLockSettings lockSettings;
    private final TtlJitter ttl;
    private final ConcurrentMap<Object, CompletableFuture<Object>> loading = new ConcurrentHashMap<>();
    private volatile long redisRetryAt;
    private final Counter hits;
    private final Counter misses;
    private final Counter stored;
    private final Counter storeRejected;
    private final Counter lockAcquired;
    private final Counter lockTimedOut;
    private final Counter lockFailed;
    private final Counter loads;

    public DistributedSingleFlightCache(String name, String keyPrefix, String lockPrefix,
                                        RedisTemplate<String, byte[]> redis, RedisSerializer<Object> serializer,
                                        CacheLockSettings lockSettings, TtlJitter ttl, MeterRegistry meterRegistry) {
        this.name = name;
        this.cacheKeyPrefix = keyPrefix + name + "::";
        this.lockKeyPrefix = lockPrefix + name + "::";
        this.redis = redis;
        this.serializer = serializer;
        this.lockSettings = lockSettings;
        this.ttl = ttl;
        this.hits = getCounter(meterRegistry, "hit");
        this.misses = getCounter(meterRegistry, "miss");
        this.stored = counter(meterRegistry, "uhsuh.cache.puts", "result", "stored");
        this.storeRejected = counter(meterRegistry, "uhsuh.cache.puts", "result", "rejected");
        this.lockAcquired = counter(meterRegistry, "uhsuh.cache.lock.attempts", "result", "acquired");
        this.lockTimedOut = counter(meterRegistry, "uhsuh.cache.lock.attempts", "result", "timeout");
        this.lockFailed = counter(meterRegistry, "uhsuh.cache.lock.attempts", "result", "error");
        this.loads = counter(meterRegistry, "uhsuh.cache.loads", "result", "computed");
    }

    public static RedisTemplate<String, byte[]> bytesTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return redis;
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
            throw new IllegalStateException("캐시 값 타입 불일치 cache=%s key=%s expected=%s actual=%s"
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
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = loading.putIfAbsent(key, mine);
        if (running != null) {
            return (T) awaitLocal(key, valueLoader, running);
        }
        try {
            T value = loadAcrossServers(key, valueLoader);
            mine.complete(value);
            return value;
        } catch (ValueRetrievalException e) {
            mine.completeExceptionally(e.getCause());
            throw e;
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            loading.remove(key, mine);
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            return;
        }
        redis.opsForValue().set(cacheKey(key), serializer.serialize(value), ttl.next());
        stored.increment();
    }

    @Override
    public void evict(Object key) {
        redis.delete(cacheKey(key));
    }

    @Override
    public void clear() {
        try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(cacheKeyPrefix + "*").count(500).build())) {
            keys.forEachRemaining(redis::delete);
        }
    }

    private Object awaitLocal(Object key, Callable<?> valueLoader, CompletableFuture<Object> running) {
        try {
            return running.get(lockSettings.localWaitLimit().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof InterruptedException && !Thread.currentThread().isInterrupted()) {
                return get(key, valueLoader);
            }
            throw new ValueRetrievalException(key, valueLoader, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValueRetrievalException(key, valueLoader, e);
        } catch (TimeoutException e) {
            Object cached = read(key);
            if (cached != null) {
                return cached;
            }
            log.warn("같은 서버의 선행 계산을 기다리다 시간 초과 cache={} key={}", name, key);
            throw new ValueRetrievalException(key, valueLoader,
                    new IllegalStateException("캐시 재생성 대기 시간 초과 cache=" + name + " key=" + key));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T loadAcrossServers(Object key, Callable<T> valueLoader) {
        String lockKey = lockKey(key);
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + lockSettings.waitTimeout().toNanos();
        while (true) {
            stopIfInterrupted(key, valueLoader);
            if (redisSkipped()) {
                return loadWithoutLock(key, valueLoader);
            }
            Boolean acquired;
            try {
                acquired = redis.opsForValue().setIfAbsent(lockKey, bytes(token), lockSettings.lockTtl());
            } catch (RuntimeException e) {
                stopIfInterrupted(key, valueLoader);
                skipRedisForAWhile();
                lockFailed.increment();
                log.warn("캐시 재생성 락을 얻지 못해 락 없이 계산합니다 cache={} key={} cause={}", name, key, e.toString());
                return loadWithoutLock(key, valueLoader);
            }
            if (Boolean.TRUE.equals(acquired)) {
                lockAcquired.increment();
                return loadUnderLock(key, valueLoader, lockKey, token);
            }
            Object cached = read(key);
            if (cached != null) {
                return (T) cached;
            }
            if (System.nanoTime() >= deadline) {
                lockTimedOut.increment();
                log.warn("캐시 재생성 락 대기 시간 초과, 락 없이 계산합니다 cache={} key={}", name, key);
                return loadWithoutLock(key, valueLoader);
            }
            pause(key, valueLoader);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T loadUnderLock(Object key, Callable<T> valueLoader, String lockKey, String token) {
        try {
            Object cached = read(key);
            if (cached != null) {
                return (T) cached;
            }
            T value = compute(key, valueLoader);
            storeIfOwner(key, value, lockKey, token);
            return value;
        } finally {
            release(lockKey, token);
        }
    }

    private <T> T loadWithoutLock(Object key, Callable<T> valueLoader) {
        T value = compute(key, valueLoader);
        storeIfAbsent(key, value);
        return value;
    }

    private <T> T compute(Object key, Callable<T> valueLoader) {
        try {
            T value = valueLoader.call();
            loads.increment();
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    private void storeIfOwner(Object key, Object value, String lockKey, String token) {
        if (value == null || redisSkipped()) {
            return;
        }
        try {
            Long result = redis.execute(STORE_IF_OWNER, List.of(lockKey, cacheKey(key)),
                    bytes(token), serializer.serialize(value), bytes(String.valueOf(ttl.next().toMillis())));
            if (result != null && result == 1L) {
                stored.increment();
                return;
            }
            storeRejected.increment();
            log.warn("락을 잃어 비어 있을 때만 저장합니다 cache={} key={}", name, key);
            storeIfAbsent(key, value);
        } catch (RuntimeException e) {
            skipRedisForAWhile();
            log.warn("캐시 저장 실패 cache={} key={} cause={}", name, key, e.toString());
        }
    }

    private void storeIfAbsent(Object key, Object value) {
        if (value == null || redisSkipped()) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cacheKey(key), serializer.serialize(value), ttl.next()))) {
                stored.increment();
            }
        } catch (RuntimeException e) {
            skipRedisForAWhile();
            log.warn("캐시 저장 실패 cache={} key={} cause={}", name, key, e.toString());
        }
    }

    private void release(String lockKey, String token) {
        if (redisSkipped()) {
            return;
        }
        boolean interrupted = Thread.interrupted();
        try {
            redis.execute(RELEASE_IF_OWNER, List.of(lockKey), bytes(token));
        } catch (RuntimeException e) {
            skipRedisForAWhile();
            log.warn("캐시 재생성 락 해제 실패, 유효 시간이 지나면 풀립니다 cache={} lock={} cause={}", name, lockKey, e.toString());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean redisSkipped() {
        long retryAt = redisRetryAt;
        return retryAt != 0 && System.nanoTime() - retryAt < 0;
    }

    private void skipRedisForAWhile() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        long retryAt = System.nanoTime() + lockSettings.redisFailureBackoff().toNanos();
        redisRetryAt = retryAt == 0 ? 1 : retryAt;
    }

    private void stopIfInterrupted(Object key, Callable<?> valueLoader) {
        if (Thread.currentThread().isInterrupted()) {
            throw new ValueRetrievalException(key, valueLoader, new InterruptedException("캐시 재생성 대기 중 인터럽트"));
        }
    }

    private void pause(Object key, Callable<?> valueLoader) {
        try {
            Thread.sleep(lockSettings.retryInterval().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    private Object lookup(Object key) {
        Object value = read(key);
        (value == null ? misses : hits).increment();
        return value;
    }

    private Object read(Object key) {
        if (redisSkipped()) {
            return null;
        }
        try {
            byte[] bytes = redis.opsForValue().get(cacheKey(key));
            return bytes == null ? null : serializer.deserialize(bytes);
        } catch (RuntimeException e) {
            skipRedisForAWhile();
            log.warn("캐시 조회 실패, 캐시 없이 진행합니다 cache={} key={} cause={}", name, key, e.toString());
            return null;
        }
    }

    private String cacheKey(Object key) {
        return cacheKeyPrefix + key;
    }

    private String lockKey(Object key) {
        return lockKeyPrefix + key;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private Counter getCounter(MeterRegistry registry, String result) {
        return Counter.builder("cache.gets")
                .tag("cache", name)
                .tag("name", name)
                .tag("cache.manager", "cacheManager")
                .tag("result", result)
                .register(registry);
    }

    private Counter counter(MeterRegistry registry, String meter, String tagKey, String tagValue) {
        return Counter.builder(meter)
                .tag("cache", name)
                .tag(tagKey, tagValue)
                .register(registry);
    }
}
