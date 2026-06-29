package uhsuhjupjup.backend.matching.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SubstringMatcherTest {

    private final SubstringMatcher matcher = new SubstringMatcher();

    private MatchCatalog catalog(MatchTarget... targets) {
        return new MatchCatalog(List.of(targets));
    }

    @Test
    void matchesCanonicalName_caseInsensitive() {
        assertThat(matcher.match("MySQL 데드락 디버깅 회고", catalog(new MatchTarget(1L, "mysql", Set.of()))))
                .extracting(KeywordMatch::keywordId, KeywordMatch::matchedVia)
                .containsExactly(tuple(1L, "title"));
    }

    @Test
    void matchesAlias() {
        assertThat(matcher.match("동시성 이슈 정리", catalog(new MatchTarget(1L, "concurrency", Set.of("동시성")))))
                .extracting(KeywordMatch::keywordId, KeywordMatch::matchedVia)
                .containsExactly(tuple(1L, "alias"));
    }

    @Test
    void prefersTitleOverAlias_whenBothPresent() {
        assertThat(matcher.match("React와 리액트 비교", catalog(new MatchTarget(1L, "react", Set.of("리액트")))))
                .extracting(KeywordMatch::matchedVia)
                .containsExactly("title");
    }

    @Test
    void matchesMultipleKeywords() {
        assertThat(matcher.match("react + kafka 아키텍처",
                catalog(new MatchTarget(1L, "react", Set.of()), new MatchTarget(2L, "kafka", Set.of()))))
                .extracting(KeywordMatch::keywordId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void noMatch_returnsEmpty() {
        assertThat(matcher.match("자바 입문 가이드", catalog(new MatchTarget(1L, "rust", Set.of()))))
                .isEmpty();
    }

    @Test
    void substringOverMatch_isDocumented() {
        assertThat(matcher.match("javascript 시작하기", catalog(new MatchTarget(1L, "java", Set.of()))))
                .extracting(KeywordMatch::matchedVia)
                .containsExactly("title");
    }

    @Test
    void blankOrNullTitle_returnsEmpty() {
        MatchCatalog catalog = catalog(new MatchTarget(1L, "java", Set.of()));
        assertThat(matcher.match("", catalog)).isEmpty();
        assertThat(matcher.match(null, catalog)).isEmpty();
    }
}
