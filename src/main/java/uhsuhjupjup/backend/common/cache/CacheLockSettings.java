package uhsuhjupjup.backend.common.cache;

import java.time.Duration;

public record CacheLockSettings(Duration lockTtl, Duration waitTimeout, Duration retryInterval,
                                Duration redisFailureBackoff) {

    public CacheLockSettings {
        requirePositive("락 유효 시간", lockTtl);
        requirePositive("락 대기 시간", waitTimeout);
        requirePositive("락 재시도 간격", retryInterval);
        requirePositive("Redis 장애 뒤 건너뛰는 시간", redisFailureBackoff);
        if (retryInterval.toMillis() < 1) {
            throw new IllegalArgumentException("락 재시도 간격은 1ms 이상이어야 합니다: " + retryInterval);
        }
    }

    public Duration localWaitLimit() {
        return waitTimeout.plus(lockTtl);
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + "은 0보다 커야 합니다: " + value);
        }
    }
}
