package uhsuhjupjup.backend.common.cache;

public interface CacheEvictBroadcaster {

    void broadcastEvict(String cacheName, Object key);

    void broadcastClear(String cacheName);

    CacheEvictBroadcaster NOOP = new CacheEvictBroadcaster() {
        @Override
        public void broadcastEvict(String cacheName, Object key) {
        }

        @Override
        public void broadcastClear(String cacheName) {
        }
    };
}
