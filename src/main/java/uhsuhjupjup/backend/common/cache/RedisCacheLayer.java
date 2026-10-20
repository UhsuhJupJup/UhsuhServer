package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache.ValueRetrievalException;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class RedisCacheLayer {

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
    private volatile long redisRetryAt;
    private final CacheMetrics metrics;
    private final Counter hits;
    private final Counter misses;
    private final Counter stored;
    private final Counter storeRejected;

    public RedisCacheLayer(String name, String keyPrefix, String lockPrefix,
                           RedisTemplate<String, byte[]> redis, RedisSerializer<Object> serializer,
                           CacheLockSettings lockSettings, TtlJitter ttl, MeterRegistry meterRegistry) {
        this.name = name;
        this.cacheKeyPrefix = keyPrefix + name + "::";
        this.lockKeyPrefix = lockPrefix + name + "::";
        this.redis = redis;
        this.serializer = serializer;
        this.lockSettings = lockSettings;
        this.ttl = ttl;
        this.metrics = new CacheMetrics(name, meterRegistry);
        this.hits = Counter.builder("cache.gets").tag("cache", name).tag("name", name)
                .tag("cache.manager", "cacheManager").tag("result", "hit").register(meterRegistry);
        this.misses = Counter.builder("cache.gets").tag("cache", name).tag("name", name)
                .tag("cache.manager", "cacheManager").tag("result", "miss").register(meterRegistry);
        this.stored = metrics.counter("uhsuh.cache.puts", "result", "stored");
        this.storeRejected = metrics.counter("uhsuh.cache.puts", "result", "rejected");
        registerMeters();
    }

    private void registerMeters() {
        for (String command : List.of("get", "lock", "store_if_owner", "store_if_absent", "release", "put", "evict")) {
            commandTimer(command, "success");
            commandTimer(command, "error");
        }
        for (String command : List.of("get", "lock", "store_if_owner", "store_if_absent", "release")) {
            metrics.counter("uhsuh.cache.redis.skipped", "command", command);
        }
        for (String source : List.of("read", "refresh")) {
            loadTimer(source, "success");
            loadTimer(source, "failure");
            for (String result : List.of("acquired", "timeout", "error")) {
                metrics.counter("uhsuh.cache.lock.attempts", "purpose", source, "result", result);
            }
            for (String result : List.of("acquired", "value_found", "timeout", "error", "interrupted", "skipped")) {
                metrics.timer("uhsuh.cache.lock.wait", "purpose", source, "result", result);
            }
        }
        for (String reason : List.of("redis_error", "redis_skipped", "lock_timeout")) {
            metrics.counter("uhsuh.cache.fallbacks", "reason", reason);
        }
    }

    public static RedisTemplate<String, byte[]> bytesTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    public Object lookup(Object key) {
        Object value = read(key);
        (value == null ? misses : hits).increment();
        return value;
    }

    @SuppressWarnings("unchecked")
    public <T> T loadAcrossServers(Object key, Callable<T> valueLoader) {
        String lockKey = lockKey(key);
        String token = UUID.randomUUID().toString();
        long started = System.nanoTime();
        long deadline = started + lockSettings.waitTimeout().toNanos();
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                lockWaited("read", "interrupted", started);
            }
            stopIfInterrupted(key, valueLoader);
            if (redisSkipped()) {
                skipped("lock");
                lockWaited("read", "skipped", started);
                return loadWithoutLock(key, valueLoader, "redis_skipped");
            }
            Boolean acquired;
            try {
                acquired = command("lock", () -> redis.opsForValue().setIfAbsent(lockKey, bytes(token), lockSettings.lockTtl()));
            } catch (RuntimeException e) {
                if (Thread.currentThread().isInterrupted()) {
                    lockWaited("read", "interrupted", started);
                }
                stopIfInterrupted(key, valueLoader);
                skipRedisForAWhile();
                lockAttempt("read", "error");
                lockWaited("read", "error", started);
                log.warn("캐시 재생성 락을 얻지 못해 락 없이 계산합니다 cache={} key={} cause={}", name, key, e.toString());
                return loadWithoutLock(key, valueLoader, "redis_error");
            }
            if (Boolean.TRUE.equals(acquired)) {
                lockAttempt("read", "acquired");
                lockWaited("read", "acquired", started);
                return loadUnderLock(key, valueLoader, lockKey, token);
            }
            Object cached = read(key);
            if (cached != null) {
                lockWaited("read", "value_found", started);
                return (T) cached;
            }
            if (System.nanoTime() >= deadline) {
                lockAttempt("read", "timeout");
                lockWaited("read", "timeout", started);
                log.warn("캐시 재생성 락 대기 시간 초과, 락 없이 계산합니다 cache={} key={}", name, key);
                return loadWithoutLock(key, valueLoader, "lock_timeout");
            }
            try {
                pause(key, valueLoader);
            } catch (ValueRetrievalException e) {
                lockWaited("read", "interrupted", started);
                throw e;
            }
        }
    }

    public RefreshOutcome refresh(Object key, Callable<?> valueLoader) {
        long started = System.nanoTime();
        if (redisSkipped()) {
            skipped("lock");
            lockWaited("refresh", "skipped", started);
            return RefreshOutcome.REDIS_UNAVAILABLE;
        }
        String lockKey = lockKey(key);
        String token = UUID.randomUUID().toString();
        long deadline = started + lockSettings.localWaitLimit().toNanos();
        while (true) {
            Boolean acquired;
            try {
                acquired = command("lock", () -> redis.opsForValue().setIfAbsent(lockKey, bytes(token), lockSettings.lockTtl()));
            } catch (RuntimeException e) {
                skipRedisForAWhile();
                lockAttempt("refresh", "error");
                lockWaited("refresh", "error", started);
                log.warn("갱신 락을 얻지 못했습니다 cache={} key={} cause={}", name, key, e.toString());
                return RefreshOutcome.REDIS_UNAVAILABLE;
            }
            if (Boolean.TRUE.equals(acquired)) {
                lockAttempt("refresh", "acquired");
                lockWaited("refresh", "acquired", started);
                return refreshUnderLock(key, valueLoader, lockKey, token);
            }
            if (System.nanoTime() >= deadline) {
                lockAttempt("refresh", "timeout");
                lockWaited("refresh", "timeout", started);
                log.warn("갱신 락 대기 시간 초과, 이번 갱신을 건너뜁니다 cache={} key={}", name, key);
                return RefreshOutcome.LOCK_TIMEOUT;
            }
            try {
                Thread.sleep(lockSettings.retryInterval().toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lockWaited("refresh", "interrupted", started);
                return RefreshOutcome.FAILED;
            }
        }
    }

    public void put(Object key, Object value) {
        if (value == null) {
            return;
        }
        command("put", () -> {
            redis.opsForValue().set(cacheKey(key), serializer.serialize(value), ttl.next());
            return null;
        });
        stored.increment();
    }

    public void evict(Object key) {
        command("evict", () -> redis.delete(cacheKey(key)));
    }

    public void clear() {
        try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(cacheKeyPrefix + "*").count(500).build())) {
            keys.forEachRemaining(redis::delete);
        }
    }

    public RedisTemplate<String, byte[]> template() {
        return redis;
    }

    public Object read(Object key) {
        if (redisSkipped()) {
            skipped("get");
            return null;
        }
        try {
            byte[] bytes = command("get", () -> redis.opsForValue().get(cacheKey(key)));
            return bytes == null ? null : serializer.deserialize(bytes);
        } catch (RuntimeException e) {
            skipRedisForAWhile();
            log.warn("캐시 조회 실패, 캐시 없이 진행합니다 cache={} key={} cause={}", name, key, e.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T loadUnderLock(Object key, Callable<T> valueLoader, String lockKey, String token) {
        try {
            Object cached = read(key);
            if (cached != null) {
                return (T) cached;
            }
            T value = compute(key, valueLoader, "read");
            if (!storeIfOwner(key, value, lockKey, token)) {
                storeIfAbsent(key, value);
            }
            return value;
        } finally {
            release(lockKey, token);
        }
    }

    private RefreshOutcome refreshUnderLock(Object key, Callable<?> valueLoader, String lockKey, String token) {
        try {
            Object value;
            try {
                value = compute(key, valueLoader, "refresh");
            } catch (ValueRetrievalException e) {
                log.warn("갱신 계산 실패 cache={} key={} cause={}", name, key, String.valueOf(e.getCause()));
                return RefreshOutcome.FAILED;
            }
            return storeIfOwner(key, value, lockKey, token) ? RefreshOutcome.STORED
                    : redisSkipped() ? RefreshOutcome.REDIS_UNAVAILABLE : RefreshOutcome.LOST_LOCK;
        } finally {
            release(lockKey, token);
        }
    }

    private <T> T loadWithoutLock(Object key, Callable<T> valueLoader, String reason) {
        metrics.counter("uhsuh.cache.fallbacks", "reason", reason).increment();
        T value = compute(key, valueLoader, "read");
        storeIfAbsent(key, value);
        return value;
    }

    private <T> T compute(Object key, Callable<T> valueLoader, String source) {
        long started = System.nanoTime();
        try {
            T value = valueLoader.call();
            loadTimer(source, "success").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            return value;
        } catch (Exception e) {
            loadTimer(source, "failure").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    private boolean storeIfOwner(Object key, Object value, String lockKey, String token) {
        if (value == null) {
            return false;
        }
        if (redisSkipped()) {
            skipped("store_if_owner");
            return false;
        }
        try {
            Long result = command("store_if_owner", () -> redis.execute(STORE_IF_OWNER, List.of(lockKey, cacheKey(key)),
                    bytes(token), serializer.serialize(value), bytes(String.valueOf(ttl.next().toMillis()))));
            if (result != null && result == 1L) {
                stored.increment();
                return true;
            }
            storeRejected.increment();
            log.warn("락을 잃어 계산 결과를 락 소유자로 저장하지 못했습니다 cache={} key={}", name, key);
            return false;
        } catch (RuntimeException e) {
            skipRedisForAWhile();
            log.warn("캐시 저장 실패 cache={} key={} cause={}", name, key, e.toString());
            return false;
        }
    }

    private void storeIfAbsent(Object key, Object value) {
        if (value == null) {
            return;
        }
        if (redisSkipped()) {
            skipped("store_if_absent");
            return;
        }
        try {
            if (Boolean.TRUE.equals(command("store_if_absent",
                    () -> redis.opsForValue().setIfAbsent(cacheKey(key), serializer.serialize(value), ttl.next())))) {
                stored.increment();
            }
        } catch (RuntimeException e) {
            skipRedisForAWhile();
            log.warn("캐시 저장 실패 cache={} key={} cause={}", name, key, e.toString());
        }
    }

    private void release(String lockKey, String token) {
        if (redisSkipped()) {
            skipped("release");
            return;
        }
        boolean interrupted = Thread.interrupted();
        try {
            command("release", () -> redis.execute(RELEASE_IF_OWNER, List.of(lockKey), bytes(token)));
        } catch (RuntimeException e) {
            skipRedisForAWhile();
            log.warn("캐시 재생성 락 해제 실패, 유효 시간이 지나면 풀립니다 cache={} lock={} cause={}", name, lockKey, e.toString());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    boolean redisSkipped() {
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

    private String cacheKey(Object key) {
        return cacheKeyPrefix + key;
    }

    private String lockKey(Object key) {
        return lockKeyPrefix + key;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private <R> R command(String command, Supplier<R> call) {
        long started = System.nanoTime();
        try {
            R result = call.get();
            commandTimer(command, "success").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            return result;
        } catch (RuntimeException e) {
            commandTimer(command, "error").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            throw e;
        }
    }

    private Timer commandTimer(String command, String result) {
        return metrics.timer("uhsuh.cache.redis.commands", "command", command, "result", result);
    }

    private Timer loadTimer(String source, String result) {
        return metrics.timer("uhsuh.cache.loads", "source", source, "result", result);
    }

    private void skipped(String command) {
        metrics.counter("uhsuh.cache.redis.skipped", "command", command).increment();
    }

    private void lockAttempt(String purpose, String result) {
        metrics.counter("uhsuh.cache.lock.attempts", "purpose", purpose, "result", result).increment();
    }

    private void lockWaited(String purpose, String result, long started) {
        metrics.timer("uhsuh.cache.lock.wait", "purpose", purpose, "result", result)
                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }
}
