package uhsuhjupjup.backend.config.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class CappedRetrySleeperTest {

    private static final Duration MAX_WAIT = Duration.ofMillis(200);
    private static final Duration NO_WAIT_BOUND = Duration.ofSeconds(5);

    private final CappedRetrySleeper sleeper = new CappedRetrySleeper(MAX_WAIT);

    @ParameterizedTest
    @ValueSource(longs = {100, 200})
    void sleep_waitUpToCap_sleepsThatLong(long waitMillis) {
        Duration wait = Duration.ofMillis(waitMillis);
        long startedAt = System.nanoTime();

        sleeper.sleep(wait);

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThanOrEqualTo(wait);
    }

    @Test
    void sleep_waitOverCap_failsWithoutSleeping() {
        long startedAt = System.nanoTime();
        Throwable failure = catchThrowable(() -> sleeper.sleep(Duration.ofSeconds(30)));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(failure)
                .isInstanceOf(LlmRetryAbortedException.class)
                .hasMessageContaining("30000ms")
                .hasMessageContaining("200ms");
        assertThat(elapsed).isLessThan(NO_WAIT_BOUND);
    }

    @Test
    void sleep_negativeWaitFromPastRetryAfterDate_returnsAtOnce() {
        long startedAt = System.nanoTime();
        sleeper.sleep(Duration.ofSeconds(-30));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed).isLessThan(NO_WAIT_BOUND);
    }

    @Test
    void sleep_interrupted_failsAndKeepsInterruptFlag() {
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> sleeper.sleep(MAX_WAIT))
                .isInstanceOf(LlmRetryAbortedException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void sleepAsync_waitUpToCap_completesAfterThatLong() throws Exception {
        long startedAt = System.nanoTime();

        sleeper.sleepAsync(MAX_WAIT).get();

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThanOrEqualTo(MAX_WAIT);
    }

    @Test
    void sleepAsync_waitOverCap_failsAtOnce() {
        CompletableFuture<Void> wait = sleeper.sleepAsync(Duration.ofSeconds(30));

        assertThat(wait).isCompletedExceptionally();
        assertThatThrownBy(wait::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(LlmRetryAbortedException.class);
    }
}
