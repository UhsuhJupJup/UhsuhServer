package uhsuhjupjup.backend.common.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public record TtlJitter(Duration min, Duration max) {

    public TtlJitter {
        if (min == null || max == null || min.isNegative() || min.isZero()) {
            throw new IllegalArgumentException("TTL 하한은 0보다 커야 합니다: " + min);
        }
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("TTL 하한이 상한보다 큽니다: " + min + " > " + max);
        }
    }

    public Duration next() {
        if (min.equals(max)) {
            return min;
        }
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(min.toMillis(), max.toMillis() + 1));
    }
}
