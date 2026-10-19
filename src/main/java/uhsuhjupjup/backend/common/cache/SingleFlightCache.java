package uhsuhjupjup.backend.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class SingleFlightCache implements Cache {

    private final Cache delegate;
    private final ConcurrentMap<Object, CompletableFuture<Object>> loading = new ConcurrentHashMap<>();

    public SingleFlightCache(Cache delegate) {
        this.delegate = delegate;
    }

    public Cache getDelegate() {
        return delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        return delegate.get(key);
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return delegate.get(key, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper cached = lookup(key);
        if (cached != null) {
            return (T) cached.get();
        }
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = loading.putIfAbsent(key, mine);
        if (running != null) {
            return (T) await(key, valueLoader, running);
        }
        try {
            T value = valueLoader.call();
            store(key, value);
            mine.complete(value);
            return value;
        } catch (Exception e) {
            mine.completeExceptionally(e);
            throw new ValueRetrievalException(key, valueLoader, e);
        } finally {
            loading.remove(key, mine);
        }
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key, value);
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    private Object await(Object key, Callable<?> valueLoader, CompletableFuture<Object> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            throw new ValueRetrievalException(key, valueLoader, e.getCause());
        }
    }

    private ValueWrapper lookup(Object key) {
        try {
            return delegate.get(key);
        } catch (RuntimeException e) {
            log.warn("캐시 조회 실패, 캐시 없이 계산합니다 cache={} key={} cause={}", getName(), key, e.toString());
            return null;
        }
    }

    private void store(Object key, Object value) {
        try {
            delegate.put(key, value);
        } catch (RuntimeException e) {
            log.warn("캐시 저장 실패 cache={} key={} cause={}", getName(), key, e.toString());
        }
    }
}
