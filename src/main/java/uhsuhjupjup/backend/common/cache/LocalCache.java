package uhsuhjupjup.backend.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LocalCache {

    private final Cache<Object, Object> store;
    private final ConcurrentMap<Object, Long> generations = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock clearLock = new ReentrantReadWriteLock();
    private volatile long epoch;

    public LocalCache(LocalCacheSettings settings, Ticker ticker) {
        this.store = Caffeine.newBuilder()
                .maximumSize(settings.maximumSize())
                .expireAfterWrite(settings.ttl())
                .ticker(ticker)
                .build();
    }

    public Object get(Object key) {
        return store.getIfPresent(key);
    }

    public Stamp stamp(Object key) {
        clearLock.readLock().lock();
        try {
            return new Stamp(epoch, generations.getOrDefault(key, 0L));
        } finally {
            clearLock.readLock().unlock();
        }
    }

    public boolean putIfUnchanged(Object key, Object value, Stamp stamp) {
        if (value == null) {
            return false;
        }
        clearLock.readLock().lock();
        try {
            if (epoch != stamp.epoch()) {
                return false;
            }
            boolean[] stored = {false};
            generations.compute(key, (k, generation) -> {
                long current = generation == null ? 0L : generation;
                if (current == stamp.generation()) {
                    store.put(k, value);
                    stored[0] = true;
                }
                return generation;
            });
            return stored[0];
        } finally {
            clearLock.readLock().unlock();
        }
    }

    public void put(Object key, Object value) {
        if (value != null) {
            putIfUnchanged(key, value, stamp(key));
        }
    }

    public void invalidate(Object key) {
        generations.compute(key, (k, generation) -> {
            store.invalidate(k);
            return (generation == null ? 0L : generation) + 1;
        });
    }

    public void invalidateAll() {
        clearLock.writeLock().lock();
        try {
            epoch++;
            store.invalidateAll();
        } finally {
            clearLock.writeLock().unlock();
        }
    }

    public long size() {
        store.cleanUp();
        return store.estimatedSize();
    }

    public record Stamp(long epoch, long generation) {
    }
}
