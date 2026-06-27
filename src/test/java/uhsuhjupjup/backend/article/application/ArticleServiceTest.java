package uhsuhjupjup.backend.article.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.support.ArticleFixture;
import uhsuhjupjup.backend.support.BlogFixture;
import uhsuhjupjup.backend.support.KeywordFixture;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleKeywordRepository articleKeywordRepository;

    @InjectMocks
    private ArticleService articleService;

    private final Blog blog = BlogFixture.blog(1L, "우아한형제들", "techblog.woowahan.com");
    private final Article article = ArticleFixture.article(
            1L, blog, "MySQL 데드락 디버깅 회고", "https://techblog.woowahan.com/12345/",
            LocalDateTime.of(2026, 6, 15, 11, 0));

    @Test
    void getDetail_returnsArticleWithKeywords() {
        Keyword mysql = KeywordFixture.keyword(3L, "MySQL");
        given(articleRepository.findWithBlogById(1L)).willReturn(Optional.of(article));
        given(articleKeywordRepository.findWithKeywordByArticleId(1L))
                .willReturn(List.of(ArticleKeyword.of(article, mysql, "title")));

        ArticleDetailResult result = articleService.getDetail(1L);

        assertThat(result.article()).isEqualTo(article);
        assertThat(result.articleKeywords()).hasSize(1);
    }

    @Test
    void getDetail_whenArticleNotFound_throws() {
        given(articleRepository.findWithBlogById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.getDetail(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARTICLE_NOT_FOUND);
    }

    @Test
    void search_returnsArticlesWithKeywordNames() {
        Keyword mysql = KeywordFixture.keyword(3L, "MySQL");
        given(articleRepository.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(article));
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(anyCollection()))
                .willReturn(List.of(ArticleKeyword.of(article, mysql, "title")));

        List<ArticleSummaryResult> result = articleService.search(null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).article()).isEqualTo(article);
        assertThat(result.get(0).keywordNames()).containsExactly("MySQL");
    }

    @Test
    void search_whenNoArticles_returnsEmpty() {
        given(articleRepository.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of());

        assertThat(articleService.search(null, null, null, null, null)).isEmpty();
    }
}
