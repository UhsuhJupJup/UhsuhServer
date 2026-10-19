package uhsuhjupjup.backend.common.cache;

import java.time.Duration;

public record LocalCacheSettings(Duration ttl, long maximumSize) {

    public LocalCacheSettings {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("로컬 캐시 TTL은 0보다 커야 합니다: " + ttl);
        }
        if (maximumSize < 1) {
            throw new IllegalArgumentException("로컬 캐시 최대 개수는 1 이상이어야 합니다: " + maximumSize);
        }
    }
}
