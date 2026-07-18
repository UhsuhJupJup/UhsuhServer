package uhsuhjupjup.backend.pipeline.matching.application;

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
import uhsuhjupjup.backend.pipeline.collection.application.FeedClient;
import uhsuhjupjup.backend.pipeline.collection.application.dto.FetchedArticle;
import uhsuhjupjup.backend.pipeline.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.support.ArticleFixture;
import uhsuhjupjup.backend.support.BlogFixture;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private KeywordAliasRepository keywordAliasRepository;

    @Mock
    private FeedClient feedClient;

    @Mock
    private KeywordClassifier keywordClassifier;

    @Mock
    private ArticleKeywordSaver articleKeywordSaver;

    @InjectMocks
    private MatchingService matchingService;

    private final Blog blog = BlogFixture.blog(1L, "b", "b.com");

    @Test
    void matchRecent_classifiesWithRefetchedBody_andAggregates() {
        Article a1 = ArticleFixture.article(1L, blog, "MySQL 데드락", "https://b.com/1", LocalDateTime.now());
        Article a2 = ArticleFixture.article(2L, blog, "무관한 글", "https://b.com/2", LocalDateTime.now());
        given(articleRepository.findPendingClassificationWithBlog(any())).willReturn(List.of(a1, a2));
        given(feedClient.fetch(any())).willReturn(List.of(
                new FetchedArticle("t1", "https://b.com/1", LocalDateTime.now(), "본문1"),
                new FetchedArticle("t2", "https://b.com/2", LocalDateTime.now(), "본문2")));
        given(keywordClassifier.classify(eq("MySQL 데드락"), eq("본문1"), any()))
                .willReturn(List.of(new KeywordMatch(3L, "ai")));
        given(keywordClassifier.classify(eq("무관한 글"), eq("본문2"), any())).willReturn(List.of());
        given(articleKeywordSaver.recordClassification(eq(1L), any(), any())).willReturn(1);
        given(articleKeywordSaver.recordClassification(eq(2L), any(), any())).willReturn(0);

        MatchingResult result = matchingService.matchRecent();

        assertThat(result.articlesScanned()).isEqualTo(2);
        assertThat(result.articlesTagged()).isEqualTo(1);
        assertThat(result.tagsCreated()).isEqualTo(1);
    }

    @Test
    void matchRecent_whenNoPendingArticles_returnsZero() {
        given(articleRepository.findPendingClassificationWithBlog(any())).willReturn(List.of());

        MatchingResult result = matchingService.matchRecent();

        assertThat(result.articlesScanned()).isZero();
        assertThat(result.tagsCreated()).isZero();
    }

    @Test
    void matchRecent_whenBodyRefetchFails_leavesArticlesPending() {
        Article a1 = ArticleFixture.article(1L, blog, "MySQL 데드락", "https://b.com/1", LocalDateTime.now());
        given(articleRepository.findPendingClassificationWithBlog(any())).willReturn(List.of(a1));
        given(feedClient.fetch(any())).willThrow(new RuntimeException("네트워크 오류"));

        MatchingResult result = matchingService.matchRecent();

        assertThat(result.articlesScanned()).isEqualTo(1);
        assertThat(result.tagsCreated()).isZero();
        verify(articleKeywordSaver, never()).recordClassification(anyLong(), any(), any());
    }
}
