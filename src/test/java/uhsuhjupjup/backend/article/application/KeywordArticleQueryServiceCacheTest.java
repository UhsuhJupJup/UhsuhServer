package uhsuhjupjup.backend.article.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.config.CacheConfig;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = {CacheConfig.class, KeywordArticleQueryService.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KeywordArticleQueryServiceCacheTest {

    @Autowired
    private KeywordArticleQueryService service;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private ArticleKeywordRepository repository;

    @BeforeEach
    void clearCache() {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.KEYWORD_ARTICLES)).clear();
    }

    @Test
    void 같은_키워드를_두_번_조회하면_DB는_한_번만_탄다() {
        given(repository.findCandidateArticleIds(eq(10L), any(), any())).willReturn(List.of(1L, 2L));

        List<Long> first = service.candidateArticleIds(10L);
        List<Long> second = service.candidateArticleIds(10L);

        assertThat(first).containsExactly(1L, 2L);
        assertThat(second).containsExactly(1L, 2L);
        verify(repository, times(1)).findCandidateArticleIds(eq(10L), any(), any());
    }

    @Test
    void 서로_다른_키워드는_각각_따로_캐싱된다() {
        given(repository.findCandidateArticleIds(eq(10L), any(), any())).willReturn(List.of(1L));
        given(repository.findCandidateArticleIds(eq(20L), any(), any())).willReturn(List.of(2L));

        service.candidateArticleIds(10L);
        service.candidateArticleIds(20L);
        service.candidateArticleIds(10L); // 10L은 캐시 히트

        verify(repository, times(1)).findCandidateArticleIds(eq(10L), any(), any());
        verify(repository, times(1)).findCandidateArticleIds(eq(20L), any(), any());
    }

    @Test
    void evict_이후에는_DB를_다시_탄다() {
        given(repository.findCandidateArticleIds(eq(30L), any(), any())).willReturn(List.of(3L));

        service.candidateArticleIds(30L);
        service.evict(30L);
        service.candidateArticleIds(30L);

        verify(repository, times(2)).findCandidateArticleIds(eq(30L), any(), any());
    }

    @Test
    void 빈_결과도_캐싱되어_관통을_막는다() {
        given(repository.findCandidateArticleIds(eq(40L), any(), any())).willReturn(List.of());

        List<Long> first = service.candidateArticleIds(40L);
        List<Long> second = service.candidateArticleIds(40L);

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        verify(repository, times(1)).findCandidateArticleIds(eq(40L), any(), any());
    }
}
