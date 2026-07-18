package uhsuhjupjup.backend.pipeline.matching.infra;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchTarget;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeKeywordClassifierLiveTest {

    private final ClaudeKeywordClassifier classifier =
            new ClaudeKeywordClassifier(AnthropicOkHttpClient.fromEnv(), "claude-haiku-4-5");

    private final MatchCatalog catalog = new MatchCatalog(List.of(
            new MatchTarget(1L, "redis", Set.of("레디스")),
            new MatchTarget(2L, "kafka", Set.of()),
            new MatchTarget(3L, "kubernetes", Set.of("k8s")),
            new MatchTarget(4L, "결제", Set.of("payment", "정산"))));

    @Test
    void classify_relevantArticle_matchesRedis() {
        List<KeywordMatch> matches = classifier.classify(
                "우리가 3배 빨라진 이유",
                "조회 성능이 병목이라 레디스 캐시를 도입했다. 자주 쓰는 데이터를 메모리에 올려 DB 부하를 줄였다.",
                catalog);

        System.out.println("[LIVE] 관련 글 매칭 = " + matches);
        assertThat(matches).extracting(KeywordMatch::keywordId).contains(1L);
        assertThat(matches).extracting(KeywordMatch::matchedVia).containsOnly("ai");
    }

    @Test
    void classify_unrelatedArticle_matchesNothing() {
        List<KeywordMatch> matches = classifier.classify(
                "재택근무 3년 회고",
                "팀 문화와 소통 방식, 일하는 리듬에 대한 개인적인 생각을 정리한 글이다.",
                catalog);

        System.out.println("[LIVE] 무관 글 매칭 = " + matches);
        assertThat(matches).isEmpty();
    }
}
