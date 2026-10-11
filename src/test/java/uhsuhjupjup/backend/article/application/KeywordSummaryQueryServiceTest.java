package uhsuhjupjup.backend.article.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import uhsuhjupjup.backend.article.application.dto.KeywordRelation;
import uhsuhjupjup.backend.article.application.dto.KeywordSummaryResult;
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
class KeywordSummaryQueryServiceTest {

    @Mock
    private ArticleKeywordRepository articleKeywordRepository;

    @InjectMocks
    private KeywordSummaryQueryService keywordSummaryQueryService;

    @Test
    void 글_수와_연관_키워드_여덟_개를_읽는다() {
        List<KeywordRelation> related = List.of(new KeywordRelation(7L, "Kafka", 2L));
        given(articleKeywordRepository.countByKeywordId(3L)).willReturn(5L);
        given(articleKeywordRepository.findRelatedKeywords(3L, PageRequest.of(0, 8))).willReturn(related);

        KeywordSummaryResult result = keywordSummaryQueryService.summarize(3L);

        assertThat(result.articleCount()).isEqualTo(5);
        assertThat(result.relatedKeywords()).isEqualTo(related);
    }

    @Test
    void 글이_없는_키워드는_연관_키워드를_조회하지_않는다() {
        given(articleKeywordRepository.countByKeywordId(3L)).willReturn(0L);

        KeywordSummaryResult result = keywordSummaryQueryService.summarize(3L);

        assertThat(result.articleCount()).isZero();
        assertThat(result.relatedKeywords()).isEmpty();
        verify(articleKeywordRepository, never()).findRelatedKeywords(anyLong(), any());
    }

    @Test
    void 글_수_조회가_실패하면_예외를_그대로_올린다() {
        given(articleKeywordRepository.countByKeywordId(3L)).willThrow(new IllegalStateException("조회 실패"));

        assertThatThrownBy(() -> keywordSummaryQueryService.summarize(3L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 연관_키워드_조회가_실패하면_예외를_그대로_올린다() {
        given(articleKeywordRepository.countByKeywordId(3L)).willReturn(5L);
        given(articleKeywordRepository.findRelatedKeywords(3L, PageRequest.of(0, 8)))
                .willThrow(new IllegalStateException("조회 실패"));

        assertThatThrownBy(() -> keywordSummaryQueryService.summarize(3L))
                .isInstanceOf(IllegalStateException.class);
    }
}
