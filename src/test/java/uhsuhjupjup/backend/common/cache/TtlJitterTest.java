package uhsuhjupjup.backend.common.cache;

import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.config.CacheConfig;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TtlJitterTest {

    @Test
    void 범위_안에서_값을_고른다() {
        TtlJitter jitter = new TtlJitter(Duration.ofMinutes(55), Duration.ofMinutes(60));

        for (int i = 0; i < 1_000; i++) {
            assertThat(jitter.next()).isBetween(Duration.ofMinutes(55), Duration.ofMinutes(60));
        }
    }

    @Test
    void 하한과_상한이_같으면_그_값을_쓴다() {
        assertThat(new TtlJitter(Duration.ofMinutes(30), Duration.ofMinutes(30)).next()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void 하한이_상한보다_크거나_0이면_거절한다() {
        assertThatThrownBy(() -> new TtlJitter(Duration.ofMinutes(61), Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TtlJitter(Duration.ZERO, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 키워드_그래프_캐시는_1시간을_넘는_상한을_거절한다() {
        assertThatThrownBy(() -> CacheConfig.keywordGraphTtl(Duration.ofMinutes(55), Duration.ofMinutes(61)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(CacheConfig.keywordGraphTtl(Duration.ofMinutes(55), Duration.ofMinutes(60)).max())
                .isEqualTo(Duration.ofHours(1));
    }
}
