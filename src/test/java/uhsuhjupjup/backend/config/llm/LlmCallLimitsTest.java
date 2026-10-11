package uhsuhjupjup.backend.config.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmCallLimitsTest {

    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration CALL = Duration.ofSeconds(10);
    private static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(2);

    @Test
    void validLimits_keepEveryValue() {
        LlmCallLimits limits = new LlmCallLimits(CONNECT, CALL, 1, MAX_RETRY_WAIT);

        assertThat(limits.connectTimeout()).isEqualTo(CONNECT);
        assertThat(limits.callTimeout()).isEqualTo(CALL);
        assertThat(limits.maxRetries()).isEqualTo(1);
        assertThat(limits.maxRetryWait()).isEqualTo(MAX_RETRY_WAIT);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 300, 30_000})
    void withoutRetry_anyRetryWaitIsAllowed(long waitMillis) {
        LlmCallLimits limits = new LlmCallLimits(CONNECT, CALL, 0, Duration.ofMillis(waitMillis));

        assertThat(limits.maxRetries()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "1, 500",
            "1, 2000",
            "2, 1000",
            "3, 2000",
            "4, 4000",
            "5, 8000",
            "10, 8000"
    })
    void retryWaitCoveringLongestSdkBackoff_isAllowed(int maxRetries, long waitMillis) {
        LlmCallLimits limits = new LlmCallLimits(CONNECT, CALL, maxRetries, Duration.ofMillis(waitMillis));

        assertThat(limits.maxRetryWait()).isEqualTo(Duration.ofMillis(waitMillis));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 0",
            "1, 300",
            "1, 499",
            "2, 999",
            "3, 1999",
            "4, 3999",
            "5, 7999",
            "10, 7999"
    })
    void retryWaitShorterThanLongestSdkBackoff_isRejected(int maxRetries, long waitMillis) {
        assertThatThrownBy(() -> new LlmCallLimits(CONNECT, CALL, maxRetries, Duration.ofMillis(waitMillis)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("재시도 횟수를 0으로");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void connectTimeoutNotPositive_isRejected(long seconds) {
        assertThatThrownBy(() -> new LlmCallLimits(Duration.ofSeconds(seconds), CALL, 1, MAX_RETRY_WAIT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void callTimeoutNotPositive_isRejected(long seconds) {
        assertThatThrownBy(() -> new LlmCallLimits(CONNECT, Duration.ofSeconds(seconds), 1, MAX_RETRY_WAIT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingTimeouts_areRejected() {
        assertThatThrownBy(() -> new LlmCallLimits(null, CALL, 1, MAX_RETRY_WAIT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmCallLimits(CONNECT, null, 1, MAX_RETRY_WAIT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeRetries_areRejected() {
        assertThatThrownBy(() -> new LlmCallLimits(CONNECT, CALL, -1, MAX_RETRY_WAIT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeOrMissingRetryWait_isRejected() {
        assertThatThrownBy(() -> new LlmCallLimits(CONNECT, CALL, 0, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmCallLimits(CONNECT, CALL, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
