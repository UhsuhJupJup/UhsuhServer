package uhsuhjupjup.backend.collection.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.collection.application.dto.FetchedArticle;
import uhsuhjupjup.backend.support.BlogFixture;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArticleSaverTest {

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private ArticleSaver articleSaver;

    @Captor
    private ArgumentCaptor<List<Article>> savedCaptor;

    private final Blog blog = BlogFixture.blog(1L, "A", "a.com");

    @Test
    void saveNew_savesOnlyNewUrls() {
        given(articleRepository.findExistingUrls(anyCollection()))
                .willReturn(List.of("https://a.com/old"));
        List<FetchedArticle> fetched = List.of(
                article("old", "https://a.com/old"),
                article("new", "https://a.com/new"));

        int saved = articleSaver.saveNew(blog, fetched);

        assertThat(saved).isEqualTo(1);
        verify(articleRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).extracting(Article::getUrl)
                .containsExactly("https://a.com/new");
    }

    @Test
    void saveNew_dropsTrackingParams_andDedupesWithinBatch() {
        given(articleRepository.findExistingUrls(anyCollection())).willReturn(List.of());
        List<FetchedArticle> fetched = List.of(
                article("a", "https://a.com/post?utm_source=rss"),
                article("a-dup", "https://a.com/post"));

        int saved = articleSaver.saveNew(blog, fetched);

        assertThat(saved).isEqualTo(1);
        verify(articleRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).extracting(Article::getUrl)
                .containsExactly("https://a.com/post");
    }

    @Test
    void saveNew_whenNoDate_fallsBackToNonNull() {
        given(articleRepository.findExistingUrls(anyCollection())).willReturn(List.of());
        FetchedArticle noDate = new FetchedArticle("t", "https://a.com/x", null);

        articleSaver.saveNew(blog, List.of(noDate));

        verify(articleRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue().get(0).getPublishedAt()).isNotNull();
    }

    @Test
    void saveNew_whenEmpty_returnsZero_andSkipsQuery() {
        int saved = articleSaver.saveNew(blog, List.of());

        assertThat(saved).isZero();
        verify(articleRepository, never()).findExistingUrls(anyCollection());
    }

    private FetchedArticle article(String title, String url) {
        return new FetchedArticle(title, url, LocalDateTime.now());
    }
}
