package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@Slf4j
public class TieredCache implements Cache {

    private final String name;
    private final LocalCache local;
    private final RedisCacheLayer remote;
    private final CacheLockSettings lockSettings;
    private final CacheInvalidationPublisher publisher;
    private final Function<String, Object> keyParser;
    private final ConcurrentMap<Object, CompletableFuture<Object>> loading = new ConcurrentHashMap<>();
    private final CacheMetrics metrics;
    private final Counter localHits;
    private final Counter localMisses;
    private final Counter localStored;
    private final Counter staleFillsSkipped;
    private final Counter leaders;
    private final Counter followers;
    private final Map<RefreshOutcome, Counter> refreshes = new EnumMap<>(RefreshOutcome.class);

    public TieredCache(String name, LocalCache local, RedisCacheLayer remote, CacheLockSettings lockSettings,
                       CacheInvalidationPublisher publisher, Function<String, Object> keyParser,
                       MeterRegistry meterRegistry) {
        this.name = name;
        this.local = local;
        this.remote = remote;
        this.lockSettings = lockSettings;
        this.publisher = publisher;
        this.keyParser = keyParser;
        this.metrics = new CacheMetrics(name, meterRegistry);
        this.localHits = metrics.counter("uhsuh.cache.local.gets", "result", "hit");
        this.localMisses = metrics.counter("uhsuh.cache.local.gets", "result", "miss");
        this.localStored = metrics.counter("uhsuh.cache.local.puts", "result", "stored");
        this.staleFillsSkipped = metrics.counter("uhsuh.cache.local.puts", "result", "skipped-stale");
        this.leaders = metrics.counter("uhsuh.cache.local.loads", "role", "leader");
        this.followers = metrics.counter("uhsuh.cache.local.loads", "role", "follower");
        for (RefreshOutcome outcome : RefreshOutcome.values()) {
            refreshes.put(outcome, metrics.counter("uhsuh.cache.refreshes", "result", outcome.name().toLowerCase()));
        }
        for (String reason : List.of("message", "redis_unavailable")) {
            metrics.counter("uhsuh.cache.local.invalidations", "reason", reason);
        }
        for (String reason : List.of("resubscribe", "clear")) {
            metrics.counter("uhsuh.cache.local.clears", "reason", reason);
        }
        publisher.register(name);
        Gauge.builder("uhsuh.cache.local.size", local, LocalCache::size).tag("cache", name).register(meterRegistry);
        FunctionCounter.builder("uhsuh.cache.local.evictions", local, LocalCache::evictionCount).tag("cache", name)
                .register(meterRegistry);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return local;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = lookupThroughLayers(key);
        return value == null ? null : new SimpleValueWrapper(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        Object value = lookupThroughLayers(key);
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException("캐시 값 타입 불일치 cache=%s key=%s expected=%s actual=%s"
                    .formatted(name, key, type.getName(), value.getClass().getName()));
        }
        return type == null ? (T) value : type.cast(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object cached = local.get(key);
        if (cached != null) {
            localHits.increment();
            return (T) cached;
        }
        localMisses.increment();
        LocalCache.Stamp stamp = local.stamp(key);
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = loading.putIfAbsent(key, mine);
        if (running != null) {
            followers.increment();
            return (T) awaitLeader(key, valueLoader, running);
        }
        leaders.increment();
        try {
            Object fromRemote = remote.lookup(key);
            T value = fromRemote != null ? (T) fromRemote : remote.loadAcrossServers(key, valueLoader);
            fillLocal(key, value, stamp);
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
        remote.put(key, value);
        local.invalidate(key);
    }

    @Override
    public void evict(Object key) {
        remote.evict(key);
        local.invalidate(key);
    }

    @Override
    public void clear() {
        remote.clear();
        local.invalidateAll();
        metrics.counter("uhsuh.cache.local.clears", "reason", "clear").increment();
    }

    public RefreshOutcome refresh(Object key, Callable<?> valueLoader) {
        RefreshOutcome outcome = remote.refresh(key, valueLoader);
        refreshes.get(outcome).increment();
        if (outcome == RefreshOutcome.STORED) {
            publisher.publishInvalidation(name, key);
        } else if (outcome == RefreshOutcome.REDIS_UNAVAILABLE) {
            invalidateLocal(key, "redis_unavailable");
        }
        return outcome;
    }

    public void invalidateLocal(Object key) {
        invalidateLocal(key, "message");
    }

    public void invalidateLocal(String rawKey) {
        invalidateLocal(keyParser.apply(rawKey), "message");
    }

    public void clearLocal() {
        local.invalidateAll();
        metrics.counter("uhsuh.cache.local.clears", "reason", "resubscribe").increment();
    }

    private void invalidateLocal(Object key, String reason) {
        local.invalidate(key);
        metrics.counter("uhsuh.cache.local.invalidations", "reason", reason).increment();
    }

    public Object localValue(Object key) {
        return local.get(key);
    }

    private Object lookupThroughLayers(Object key) {
        Object cached = local.get(key);
        if (cached != null) {
            localHits.increment();
            return cached;
        }
        localMisses.increment();
        LocalCache.Stamp stamp = local.stamp(key);
        Object value = remote.lookup(key);
        fillLocal(key, value, stamp);
        return value;
    }

    private void fillLocal(Object key, Object value, LocalCache.Stamp stamp) {
        if (value == null) {
            return;
        }
        if (local.putIfUnchanged(key, value, stamp)) {
            localStored.increment();
        } else {
            staleFillsSkipped.increment();
            log.debug("조회 중 무효화가 있어 로컬 캐시에 담지 않았습니다 cache={} key={}", name, key);
        }
    }

    private Object awaitLeader(Object key, Callable<?> valueLoader, CompletableFuture<Object> running) {
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
            Object cached = local.get(key);
            if (cached == null) {
                cached = remote.read(key);
            }
            if (cached != null) {
                return cached;
            }
            log.warn("같은 서버의 선행 계산을 기다리다 시간 초과 cache={} key={}", name, key);
            throw new ValueRetrievalException(key, valueLoader,
                    new IllegalStateException("캐시 재생성 대기 시간 초과 cache=" + name + " key=" + key));
        }
    }
}
