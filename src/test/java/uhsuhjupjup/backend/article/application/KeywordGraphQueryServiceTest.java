package uhsuhjupjup.backend.article.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import uhsuhjupjup.backend.article.application.dto.KeywordArticleCount;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborLink;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborhoodResult;
import uhsuhjupjup.backend.article.application.dto.KeywordRelation;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KeywordGraphQueryServiceTest {

    @Mock
    private ArticleKeywordRepository articleKeywordRepository;

    @InjectMocks
    private KeywordGraphQueryService keywordGraphQueryService;

    @Test
    void 글_수와_이웃_열_개와_이웃끼리의_연결을_읽는다() {
        List<KeywordRelation> neighbors = List.of(
                new KeywordRelation(7L, "Kafka", 4L),
                new KeywordRelation(9L, "Redis", 2L));
        List<KeywordNeighborLink> links = List.of(new KeywordNeighborLink(7L, 9L, 1L));
        given(articleKeywordRepository.countByKeywordId(3L)).willReturn(5L);
        given(articleKeywordRepository.findRelatedKeywords(3L, PageRequest.of(0, 10))).willReturn(neighbors);
        given(articleKeywordRepository.findNeighborLinks(3L, List.of(7L, 9L))).willReturn(links);

        KeywordNeighborhoodResult result = keywordGraphQueryService.findNeighborhood(3L);

        assertThat(result.articleCount()).isEqualTo(5);
        assertThat(result.neighbors()).isEqualTo(neighbors);
        assertThat(result.neighborLinks()).isEqualTo(links);
    }

    @Test
    void 글이_없는_키워드는_이웃과_연결을_조회하지_않는다() {
        given(articleKeywordRepository.countByKeywordId(3L)).willReturn(0L);

        KeywordNeighborhoodResult result = keywordGraphQueryService.findNeighborhood(3L);

        assertThat(result.articleCount()).isZero();
        assertThat(result.neighbors()).isEmpty();
        assertThat(result.neighborLinks()).isEmpty();
        verify(articleKeywordRepository, never()).findRelatedKeywords(anyLong(), any());
        verify(articleKeywordRepository, never()).findNeighborLinks(anyLong(), any());
    }

    @Test
    void 이웃이_하나뿐이면_이웃끼리의_연결을_조회하지_않는다() {
        List<KeywordRelation> neighbors = List.of(new KeywordRelation(7L, "Kafka", 4L));
        given(articleKeywordRepository.countByKeywordId(3L)).willReturn(5L);
        given(articleKeywordRepository.findRelatedKeywords(3L, PageRequest.of(0, 10))).willReturn(neighbors);

        KeywordNeighborhoodResult result = keywordGraphQueryService.findNeighborhood(3L);

        assertThat(result.neighbors()).isEqualTo(neighbors);
        assertThat(result.neighborLinks()).isEmpty();
        verify(articleKeywordRepository, never()).findNeighborLinks(anyLong(), any());
    }

    @Test
    void 이웃이_없으면_이웃끼리의_연결을_조회하지_않는다() {
        given(articleKeywordRepository.countByKeywordId(3L)).willReturn(5L);
        given(articleKeywordRepository.findRelatedKeywords(3L, PageRequest.of(0, 10))).willReturn(List.of());

        KeywordNeighborhoodResult result = keywordGraphQueryService.findNeighborhood(3L);

        assertThat(result.articleCount()).isEqualTo(5);
        assertThat(result.neighbors()).isEmpty();
        assertThat(result.neighborLinks()).isEmpty();
        verify(articleKeywordRepository, never()).findNeighborLinks(anyLong(), any());
    }

    @Test
    void 글_수_조회가_실패하면_예외를_그대로_올린다() {
        given(articleKeywordRepository.countByKeywordId(3L)).willThrow(new IllegalStateException("조회 실패"));

        assertThatThrownBy(() -> keywordGraphQueryService.findNeighborhood(3L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 이웃_조회가_실패하면_예외를_그대로_올린다() {
        given(articleKeywordRepository.countByKeywordId(3L)).willReturn(5L);
        given(articleKeywordRepository.findRelatedKeywords(3L, PageRequest.of(0, 10)))
                .willThrow(new IllegalStateException("조회 실패"));

        assertThatThrownBy(() -> keywordGraphQueryService.findNeighborhood(3L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 이웃끼리의_연결_조회가_실패하면_예외를_그대로_올린다() {
        given(articleKeywordRepository.countByKeywordId(3L)).willReturn(5L);
        given(articleKeywordRepository.findRelatedKeywords(3L, PageRequest.of(0, 10))).willReturn(List.of(
                new KeywordRelation(7L, "Kafka", 4L),
                new KeywordRelation(9L, "Redis", 2L)));
        given(articleKeywordRepository.findNeighborLinks(3L, List.of(7L, 9L)))
                .willThrow(new IllegalStateException("조회 실패"));

        assertThatThrownBy(() -> keywordGraphQueryService.findNeighborhood(3L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 글이_가장_많은_키워드_하나를_읽는다() {
        KeywordArticleCount top = new KeywordArticleCount(3L, "MySQL", 5L);
        given(articleKeywordRepository.findTopKeywords(PageRequest.of(0, 1))).willReturn(List.of(top));

        assertThat(keywordGraphQueryService.findTop()).contains(top);
    }

    @Test
    void 글이_붙은_키워드가_없으면_가장_많은_키워드는_비어_있다() {
        given(articleKeywordRepository.findTopKeywords(PageRequest.of(0, 1))).willReturn(List.of());

        assertThat(keywordGraphQueryService.findTop()).isEmpty();
    }
}
