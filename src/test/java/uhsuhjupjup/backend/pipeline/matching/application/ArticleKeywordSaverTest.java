package uhsuhjupjup.backend.pipeline.matching.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.article.application.KeywordArticleQueryService;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.support.ArticleFixture;
import uhsuhjupjup.backend.support.BlogFixture;
import uhsuhjupjup.backend.support.KeywordFixture;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArticleKeywordSaverTest {

    @Mock
    private ArticleKeywordRepository articleKeywordRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private KeywordArticleQueryService keywordArticleQueryService;

    @InjectMocks
    private ArticleKeywordSaver saver;

    @Captor
    private ArgumentCaptor<List<ArticleKeyword>> savedCaptor;

    private final Blog blog = BlogFixture.blog(1L, "b", "b.com");
    private final Article article = ArticleFixture.article(10L, blog, "t", "https://b.com/t", LocalDateTime.now());
    private final LocalDateTime classifiedAt = LocalDateTime.of(2026, 7, 19, 6, 0);

    @Test
    void recordClassification_savesOnlyNewKeywords_andMarks() {
        Keyword keyword2 = KeywordFixture.keyword(2L, "React");
        given(articleRepository.findById(10L)).willReturn(Optional.of(article));
        given(articleKeywordRepository.findKeywordIdsByArticleId(10L)).willReturn(List.of(1L));
        given(keywordRepository.getReferenceById(2L)).willReturn(keyword2);

        int saved = saver.recordClassification(10L,
                List.of(new KeywordMatch(1L, "title"), new KeywordMatch(2L, "alias")), classifiedAt);

        assertThat(saved).isEqualTo(1);
        verify(articleKeywordRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).hasSize(1);
        assertThat(savedCaptor.getValue().get(0).getKeyword()).isEqualTo(keyword2);
        assertThat(savedCaptor.getValue().get(0).getMatchedVia()).isEqualTo("alias");
        assertThat(article.getClassifiedAt()).isEqualTo(classifiedAt);
    }

    @Test
    void recordClassification_whenAllExisting_savesNothing_butMarks() {
        given(articleRepository.findById(10L)).willReturn(Optional.of(article));
        given(articleKeywordRepository.findKeywordIdsByArticleId(10L)).willReturn(List.of(1L, 2L));

        int saved = saver.recordClassification(10L,
                List.of(new KeywordMatch(1L, "title"), new KeywordMatch(2L, "alias")), classifiedAt);

        assertThat(saved).isZero();
        verify(keywordRepository, never()).getReferenceById(anyLong());
        assertThat(article.getClassifiedAt()).isEqualTo(classifiedAt);
    }

    @Test
    void recordClassification_whenEmpty_marksAndSkipsTagQuery() {
        given(articleRepository.findById(10L)).willReturn(Optional.of(article));

        int saved = saver.recordClassification(10L, List.of(), classifiedAt);

        assertThat(saved).isZero();
        verify(articleKeywordRepository, never()).findKeywordIdsByArticleId(anyLong());
        assertThat(article.getClassifiedAt()).isEqualTo(classifiedAt);
    }
}
