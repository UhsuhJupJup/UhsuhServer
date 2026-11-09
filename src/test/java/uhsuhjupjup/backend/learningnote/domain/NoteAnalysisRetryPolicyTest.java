package uhsuhjupjup.backend.learningnote.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteAnalysisRetryPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 10, 0);
    private final NoteAnalysisRetryPolicy policy = new NoteAnalysisRetryPolicy(
            Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(6), 20, Duration.ofSeconds(30));

    @Test
    void 간격은_두_배씩_늘고_최대_간격을_넘지_않는다() {
        assertThat(policy.nextAttemptAt(1, NOW)).isEqualTo(NOW.plusSeconds(30));
        assertThat(policy.nextAttemptAt(2, NOW)).isEqualTo(NOW.plusSeconds(60));
        assertThat(policy.nextAttemptAt(5, NOW)).isEqualTo(NOW.plusMinutes(8));
        assertThat(policy.nextAttemptAt(6, NOW)).isEqualTo(NOW.plusMinutes(15));
        assertThat(policy.nextAttemptAt(60, NOW)).isEqualTo(NOW.plusMinutes(15));
    }

    @Test
    void 시간_지평이나_최대_시도에_닿으면_끝낸다() {
        assertThat(policy.exhausted(1, NOW, NOW.plusHours(5))).isFalse();
        assertThat(policy.exhausted(1, NOW, NOW.plusHours(6))).isTrue();
        assertThat(policy.exhausted(20, NOW, NOW)).isTrue();
    }

    @Test
    void 최대_간격이_기본_간격보다_짧으면_만들_수_없다() {
        assertThatThrownBy(() -> new NoteAnalysisRetryPolicy(
                Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofHours(1), 3, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
