package uhsuhjupjup.backend.keyword.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborLink;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborhoodResult;
import uhsuhjupjup.backend.article.application.dto.KeywordRelation;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.config.CacheConfig;
import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = {CacheConfig.class, KeywordGraphProvider.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KeywordGraphProviderCacheTest {

    @Autowired
    private KeywordGraphProvider provider;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private KeywordService keywordService;

    @BeforeEach
    void clearCache() {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.KEYWORD_GRAPH)).clear();
    }

    @Test
    void 같은_키워드를_두_번_조회하면_집계는_한_번만_한다() {
        given(keywordService.getGraph(3L)).willReturn(graph(3L, "MySQL"));

        KeywordGraphResult first = provider.keywordGraph(3L);
        KeywordGraphResult second = provider.keywordGraph(3L);

        assertThat(first).isEqualTo(graph(3L, "MySQL"));
        assertThat(second).isEqualTo(first);
        verify(keywordService, times(1)).getGraph(3L);
    }

    @Test
    void 서로_다른_키워드는_따로_담는다() {
        given(keywordService.getGraph(3L)).willReturn(graph(3L, "MySQL"));
        given(keywordService.getGraph(7L)).willReturn(graph(7L, "Kafka"));

        provider.keywordGraph(3L);
        provider.keywordGraph(7L);
        KeywordGraphResult again = provider.keywordGraph(7L);

        assertThat(again.keywordName()).isEqualTo("Kafka");
        verify(keywordService, times(1)).getGraph(3L);
        verify(keywordService, times(1)).getGraph(7L);
    }

    @Test
    void 없는_키워드는_담지_않고_예외를_그대로_던진다() {
        given(keywordService.getGraph(99L)).willThrow(new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));

        assertThatThrownBy(() -> provider.keywordGraph(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KEYWORD_NOT_FOUND));
        assertThatThrownBy(() -> provider.keywordGraph(99L)).isInstanceOf(BusinessException.class);

        verify(keywordService, times(2)).getGraph(99L);
    }

    private KeywordGraphResult graph(Long keywordId, String name) {
        return new KeywordGraphResult(keywordId, name, new KeywordNeighborhoodResult(
                5,
                List.of(new KeywordRelation(11L, "Redis", 2L), new KeywordRelation(12L, "Spring", 1L)),
                List.of(new KeywordNeighborLink(11L, 12L, 1L))));
    }
}
