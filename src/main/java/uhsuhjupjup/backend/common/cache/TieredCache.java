package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class TieredCache implements Cache {

    private final String name;
    private final LocalCache local;
    private final RedisCacheLayer remote;
    private final CacheLockSettings lockSettings;
    private final ConcurrentMap<Object, CompletableFuture<Object>> loading = new ConcurrentHashMap<>();
    private final Counter localHits;
    private final Counter localMisses;

    public TieredCache(String name, LocalCache local, RedisCacheLayer remote, CacheLockSettings lockSettings,
                       MeterRegistry meterRegistry) {
        this.name = name;
        this.local = local;
        this.remote = remote;
        this.lockSettings = lockSettings;
        this.localHits = counter(meterRegistry, "uhsuh.cache.local.gets", "hit");
        this.localMisses = counter(meterRegistry, "uhsuh.cache.local.gets", "miss");
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
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = loading.putIfAbsent(key, mine);
        if (running != null) {
            return (T) awaitLeader(key, valueLoader, running);
        }
        try {
            Object fromRemote = remote.lookup(key);
            T value = fromRemote != null ? (T) fromRemote : remote.loadAcrossServers(key, valueLoader);
            local.put(key, value);
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
        Object value = remote.lookup(key);
        local.put(key, value);
        return value;
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

    private Counter counter(MeterRegistry registry, String meter, String result) {
        return Counter.builder(meter).tag("cache", name).tag("result", result).register(registry);
    }
}
