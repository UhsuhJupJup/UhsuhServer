package uhsuhjupjup.backend.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

public class LocalCache {

    private final Cache<Object, Object> store;

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

    public void put(Object key, Object value) {
        if (value != null) {
            store.put(key, value);
        }
    }

    public void invalidate(Object key) {
        store.invalidate(key);
    }

    public void invalidateAll() {
        store.invalidateAll();
    }

    public long size() {
        store.cleanUp();
        return store.estimatedSize();
    }
}
