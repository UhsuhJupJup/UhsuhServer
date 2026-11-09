package uhsuhjupjup.backend.learningnote.domain;

import java.time.Duration;
import java.time.LocalDateTime;

public record NoteAnalysisRetryPolicy(Duration baseDelay, Duration maxDelay, Duration horizon, int maxAttempts,
                                      Duration circuitOpenDelay) {

    public NoteAnalysisRetryPolicy {
        requirePositive(baseDelay, "재시도 기본 간격");
        requirePositive(maxDelay, "재시도 최대 간격");
        requirePositive(horizon, "재시도 시간 지평");
        requirePositive(circuitOpenDelay, "서킷 열림 연기 간격");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("최대 시도 횟수는 1 이상이어야 합니다: " + maxAttempts);
        }
        if (maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("재시도 최대 간격이 기본 간격보다 짧습니다");
        }
    }

    public LocalDateTime nextAttemptAt(int failedAttempts, LocalDateTime now) {
        int exponent = Math.min(Math.max(failedAttempts - 1, 0), 20);
        Duration delay = baseDelay.multipliedBy(1L << exponent);
        return now.plus(delay.compareTo(maxDelay) > 0 ? maxDelay : delay);
    }

    public LocalDateTime circuitOpenUntil(LocalDateTime now) {
        return now.plus(circuitOpenDelay);
    }

    public boolean exhausted(int failedAttempts, LocalDateTime queuedAt, LocalDateTime now) {
        return failedAttempts >= maxAttempts || !now.isBefore(queuedAt.plus(horizon));
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "은 0보다 커야 합니다: " + value);
        }
    }
}
