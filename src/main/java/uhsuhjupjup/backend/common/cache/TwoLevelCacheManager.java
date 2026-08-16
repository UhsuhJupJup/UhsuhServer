package uhsuhjupjup.backend.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TwoLevelCacheManager implements CacheManager {

    private final Map<String, TwoLevelCache> caches;

    public TwoLevelCacheManager(List<TwoLevelCache> cacheList) {
        Map<String, TwoLevelCache> map = new LinkedHashMap<>();
        for (TwoLevelCache cache : cacheList) {
            map.put(cache.getName(), cache);
        }
        this.caches = Map.copyOf(map);
    }

    @Override
    public Cache getCache(String name) {
        return caches.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }
}
