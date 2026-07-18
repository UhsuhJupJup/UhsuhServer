package uhsuhjupjup.backend.pipeline.matching.domain;

import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.keyword.domain.KeywordAlias;
import uhsuhjupjup.backend.support.KeywordFixture;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordResolverTest {

    private final MatchCatalog catalog = MatchCatalog.from(
            List.of(
                    KeywordFixture.keyword(1L, "Kafka"),
                    KeywordFixture.keyword(2L, "Redis"),
                    KeywordFixture.keyword(3L, "Kubernetes")),
            List.of(
                    KeywordAlias.create(2L, "레디스"),
                    KeywordAlias.create(3L, "k8s")));

    @Test
    void 존재하는_키워드만_남기고_대소문자를_무시한다() {
        List<KeywordMatch> result = KeywordResolver.resolve(
                List.of("kafka", "존재안함", "REDIS"), catalog, "ai");

        assertThat(result).extracting(KeywordMatch::keywordId).containsExactly(1L, 2L);
    }

    @Test
    void 별칭도_키워드로_매핑한다() {
        List<KeywordMatch> result = KeywordResolver.resolve(
                List.of("레디스", "k8s"), catalog, "ai");

        assertThat(result).extracting(KeywordMatch::keywordId).containsExactly(2L, 3L);
    }

    @Test
    void 같은_키워드로_수렴하는_중복은_한번만_남긴다() {
        List<KeywordMatch> result = KeywordResolver.resolve(
                List.of("redis", "레디스", "REDIS"), catalog, "ai");

        assertThat(result).extracting(KeywordMatch::keywordId).containsExactly(2L);
    }

    @Test
    void null과_빈문자열은_무시한다() {
        List<KeywordMatch> result = KeywordResolver.resolve(
                Arrays.asList("kafka", null, "   ", "redis"), catalog, "ai");

        assertThat(result).extracting(KeywordMatch::keywordId).containsExactly(1L, 2L);
    }

    @Test
    void 빈_입력은_빈_결과다() {
        assertThat(KeywordResolver.resolve(List.of(), catalog, "ai")).isEmpty();
    }

    @Test
    void 매칭_출처를_그대로_기록한다() {
        List<KeywordMatch> result = KeywordResolver.resolve(List.of("kafka"), catalog, "ai");

        assertThat(result).singleElement()
                .extracting(KeywordMatch::matchedVia).isEqualTo("ai");
    }
}
