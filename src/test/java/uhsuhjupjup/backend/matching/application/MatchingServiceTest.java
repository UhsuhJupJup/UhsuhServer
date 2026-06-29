package uhsuhjupjup.backend.matching.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.matching.domain.KeywordMatcher;
import uhsuhjupjup.backend.support.ArticleFixture;
import uhsuhjupjup.backend.support.BlogFixture;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private KeywordAliasRepository keywordAliasRepository;

    @Mock
    private KeywordMatcher keywordMatcher;

    @Mock
    private ArticleKeywordSaver articleKeywordSaver;

    @InjectMocks
    private MatchingService matchingService;

    private final Blog blog = BlogFixture.blog(1L, "b", "b.com");

    @Test
    void matchRecent_tagsWindowArticles_andAggregates() {
        Article a1 = ArticleFixture.article(1L, blog, "MySQL 데드락", "https://b.com/1", LocalDateTime.now());
        Article a2 = ArticleFixture.article(2L, blog, "무관한 글", "https://b.com/2", LocalDateTime.now());
        given(articleRepository.findByCollectedAtGreaterThanEqual(any())).willReturn(List.of(a1, a2));
        given(keywordMatcher.match(eq("MySQL 데드락"), any())).willReturn(List.of(new KeywordMatch(3L, "title")));
        given(keywordMatcher.match(eq("무관한 글"), any())).willReturn(List.of());
        given(articleKeywordSaver.saveNewTags(eq(1L), any())).willReturn(1);

        MatchingResult result = matchingService.matchRecent();

        assertThat(result.articlesScanned()).isEqualTo(2);
        assertThat(result.articlesTagged()).isEqualTo(1);
        assertThat(result.tagsCreated()).isEqualTo(1);
    }

    @Test
    void matchRecent_whenNoArticles_returnsZero() {
        given(articleRepository.findByCollectedAtGreaterThanEqual(any())).willReturn(List.of());

        MatchingResult result = matchingService.matchRecent();

        assertThat(result.articlesScanned()).isZero();
        assertThat(result.tagsCreated()).isZero();
    }
}
