package uhsuhjupjup.backend.pipeline.matching.infra;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ResilientKeywordClassifierTest {

    private final MatchCatalog catalog = new MatchCatalog(List.of());
    private final List<KeywordMatch> claudeMatches = List.of(new KeywordMatch(1L, "ai"));
    private final List<KeywordMatch> gptMatches = List.of(new KeywordMatch(2L, "ai"));

    private final ClaudeKeywordClassifier claude = mock(ClaudeKeywordClassifier.class);
    private final GptKeywordClassifier gpt = mock(GptKeywordClassifier.class);

    @Test
    void claude가_성공하면_claude_결과를_쓰고_gpt는_호출하지_않는다() {
        given(claude.classify(any(), any(), any())).willReturn(claudeMatches);
        ResilientKeywordClassifier classifier = classifierWith(gpt);

        List<KeywordMatch> result = classifier.classify("제목", "본문", catalog);

        assertThat(result).isEqualTo(claudeMatches);
        verify(gpt, never()).classify(any(), any(), any());
    }

    @Test
    void claude가_실패하면_gpt로_폴백한다() {
        given(claude.classify(any(), any(), any())).willThrow(new RuntimeException("Claude 타임아웃"));
        given(gpt.classify(any(), any(), any())).willReturn(gptMatches);
        ResilientKeywordClassifier classifier = classifierWith(gpt);

        List<KeywordMatch> result = classifier.classify("제목", "본문", catalog);

        assertThat(result).isEqualTo(gptMatches);
    }

    @Test
    void claude와_gpt가_모두_실패하면_예외를_던진다() {
        given(claude.classify(any(), any(), any())).willThrow(new RuntimeException("Claude 실패"));
        given(gpt.classify(any(), any(), any())).willThrow(new RuntimeException("GPT 실패"));
        ResilientKeywordClassifier classifier = classifierWith(gpt);

        assertThatThrownBy(() -> classifier.classify("제목", "본문", catalog))
                .isInstanceOf(KeywordClassificationException.class);
    }

    @Test
    void claude가_실패하고_gpt가_없으면_예외를_던진다() {
        given(claude.classify(any(), any(), any())).willThrow(new RuntimeException("Claude 실패"));
        ResilientKeywordClassifier classifier = classifierWith(null);

        assertThatThrownBy(() -> classifier.classify("제목", "본문", catalog))
                .isInstanceOf(KeywordClassificationException.class);
    }

    private ResilientKeywordClassifier classifierWith(GptKeywordClassifier gptBean) {
        @SuppressWarnings("unchecked")
        ObjectProvider<GptKeywordClassifier> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(gptBean);
        return new ResilientKeywordClassifier(CircuitBreakerRegistry.ofDefaults(), claude, provider);
    }
}
