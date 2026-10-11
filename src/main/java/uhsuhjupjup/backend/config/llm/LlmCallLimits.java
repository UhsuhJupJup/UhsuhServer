package uhsuhjupjup.backend.config.llm;

import java.time.Duration;

public record LlmCallLimits(Duration connectTimeout, Duration callTimeout, int maxRetries, Duration maxRetryWait) {

    private static final double SDK_FIRST_BACKOFF_SECONDS = 0.5;
    private static final double SDK_MAX_BACKOFF_SECONDS = 8.0;

    public LlmCallLimits {
        requirePositive(connectTimeout, "연결 상한");
        requirePositive(callTimeout, "요청 상한");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("재시도 횟수는 0 이상이어야 합니다: " + maxRetries);
        }
        if (maxRetryWait == null || maxRetryWait.isNegative()) {
            throw new IllegalArgumentException("재시도 대기 상한은 0 이상이어야 합니다: " + maxRetryWait);
        }
        if (maxRetries > 0 && maxRetryWait.compareTo(longestSdkBackoff(maxRetries)) < 0) {
            throw new IllegalArgumentException("재시도 대기 상한 " + maxRetryWait.toMillis()
                    + "ms가 SDK의 재시도 간격 최대 " + longestSdkBackoff(maxRetries).toMillis()
                    + "ms보다 짧아 재시도가 일어나지 않습니다. 재시도를 끄려면 재시도 횟수를 0으로 두세요");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "은 0보다 커야 합니다: " + value);
        }
    }

    private static Duration longestSdkBackoff(int maxRetries) {
        double seconds = Math.min(SDK_FIRST_BACKOFF_SECONDS * Math.pow(2, maxRetries - 1), SDK_MAX_BACKOFF_SECONDS);
        return Duration.ofMillis(Math.round(seconds * 1000));
    }
}
