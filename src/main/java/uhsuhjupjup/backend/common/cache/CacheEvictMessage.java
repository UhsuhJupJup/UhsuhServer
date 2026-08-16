package uhsuhjupjup.backend.common.cache;

public record CacheEvictMessage(String cacheName, String key, String senderId) {
}
