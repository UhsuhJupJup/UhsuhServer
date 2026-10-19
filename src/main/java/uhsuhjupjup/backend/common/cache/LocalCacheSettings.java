package uhsuhjupjup.backend.common.cache;

public record LocalCacheSettings(TtlJitter ttl, long maximumSize) {

    public LocalCacheSettings {
        if (ttl == null) {
            throw new IllegalArgumentException("로컬 캐시 TTL이 필요합니다");
        }
        if (maximumSize < 1) {
            throw new IllegalArgumentException("로컬 캐시 최대 개수는 1 이상이어야 합니다: " + maximumSize);
        }
    }
}
