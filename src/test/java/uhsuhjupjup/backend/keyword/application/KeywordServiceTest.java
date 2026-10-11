package uhsuhjupjup.backend.keyword.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.article.application.KeywordGraphQueryService;
import uhsuhjupjup.backend.article.application.KeywordSummaryQueryService;
import uhsuhjupjup.backend.article.application.dto.KeywordArticleCount;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborLink;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborhoodResult;
import uhsuhjupjup.backend.article.application.dto.KeywordRelation;
import uhsuhjupjup.backend.article.application.dto.KeywordSummaryResult;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.keyword.application.dto.KeywordDetailResult;
import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.support.KeywordFixture;
import uhsuhjupjup.backend.support.TopicFixture;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.domain.TopicKeyword;
import uhsuhjupjup.backend.topic.infra.TopicKeywordRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class KeywordServiceTest {

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private TopicKeywordRepository topicKeywordRepository;

    @Mock
    private KeywordSummaryQueryService keywordSummaryQueryService;

    @Mock
    private KeywordGraphQueryService keywordGraphQueryService;

    @InjectMocks
    private KeywordService keywordService;

    @Test
    void search_withBlankQueryAndNoTopic_returnsAll() {
        List<Keyword> all = List.of(KeywordFixture.keyword(1L, "Kafka"), KeywordFixture.keyword(2L, "Redis"));
        given(keywordRepository.findAllByOrderByNameAsc()).willReturn(all);

        assertThat(keywordService.search("  ", null)).isEqualTo(all);
    }

    @Test
    void search_withQuery_searchesByNameOrAlias() {
        List<Keyword> matched = List.of(KeywordFixture.keyword(3L, "MySQL"));
        given(keywordRepository.searchByNameOrAlias("마이")).willReturn(matched);

        assertThat(keywordService.search("마이", null)).isEqualTo(matched);
    }

    @Test
    void search_withTopicId_returnsTopicKeywords() {
        Topic topic = TopicFixture.topic(1L, "Database");
        Keyword mysql = KeywordFixture.keyword(3L, "MySQL");
        Keyword redis = KeywordFixture.keyword(1L, "Redis");
        given(topicKeywordRepository.findWithKeywordByTopicId(1L))
                .willReturn(List.of(TopicKeyword.of(topic, mysql), TopicKeyword.of(topic, redis)));

        assertThat(keywordService.search(null, 1L)).containsExactly(mysql, redis);
    }

    @Test
    void search_withTopicIdAndQuery_filtersTopicKeywordsByName() {
        Topic topic = TopicFixture.topic(1L, "Database");
        Keyword mysql = KeywordFixture.keyword(3L, "MySQL");
        Keyword redis = KeywordFixture.keyword(1L, "Redis");
        given(topicKeywordRepository.findWithKeywordByTopicId(1L))
                .willReturn(List.of(TopicKeyword.of(topic, mysql), TopicKeyword.of(topic, redis)));

        assertThat(keywordService.search("sql", 1L)).containsExactly(mysql);
    }

    @Test
    void getDetail_returnsKeywordWithTopicsAndSummary() {
        Keyword keyword = KeywordFixture.keyword(3L, "MySQL");
        Topic database = TopicFixture.topic(1L, "Database");
        KeywordSummaryResult summary = new KeywordSummaryResult(5, List.of(new KeywordRelation(7L, "Kafka", 2L)));
        given(keywordRepository.findById(3L)).willReturn(Optional.of(keyword));
        given(topicKeywordRepository.findWithTopicByKeywordId(3L))
                .willReturn(List.of(TopicKeyword.of(database, keyword)));
        given(keywordSummaryQueryService.summarize(3L)).willReturn(summary);

        KeywordDetailResult result = keywordService.getDetail(3L);

        assertThat(result.keyword()).isEqualTo(keyword);
        assertThat(result.topics()).containsExactly(database);
        assertThat(result.summary()).isEqualTo(summary);
    }

    @Test
    void getDetail_whenSummaryFails_propagates() {
        Keyword keyword = KeywordFixture.keyword(3L, "MySQL");
        given(keywordRepository.findById(3L)).willReturn(Optional.of(keyword));
        given(keywordSummaryQueryService.summarize(3L)).willThrow(new IllegalStateException("조회 실패"));

        assertThatThrownBy(() -> keywordService.getDetail(3L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getDetail_whenKeywordNotFound_throws() {
        given(keywordRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> keywordService.getDetail(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KEYWORD_NOT_FOUND);
        verifyNoInteractions(keywordSummaryQueryService);
    }

    @Test
    void getGraph_returnsKeywordWithNeighborhood() {
        Keyword keyword = KeywordFixture.keyword(3L, "MySQL");
        KeywordNeighborhoodResult neighborhood = new KeywordNeighborhoodResult(
                5,
                List.of(new KeywordRelation(7L, "Kafka", 2L), new KeywordRelation(9L, "Redis", 1L)),
                List.of(new KeywordNeighborLink(7L, 9L, 1L)));
        given(keywordRepository.findById(3L)).willReturn(Optional.of(keyword));
        given(keywordGraphQueryService.findNeighborhood(3L)).willReturn(neighborhood);

        KeywordGraphResult result = keywordService.getGraph(3L);

        assertThat(result.keyword()).isEqualTo(keyword);
        assertThat(result.neighborhood()).isEqualTo(neighborhood);
    }

    @Test
    void getGraph_whenNeighborhoodFails_propagates() {
        given(keywordRepository.findById(3L)).willReturn(Optional.of(KeywordFixture.keyword(3L, "MySQL")));
        given(keywordGraphQueryService.findNeighborhood(3L)).willThrow(new IllegalStateException("조회 실패"));

        assertThatThrownBy(() -> keywordService.getGraph(3L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getGraph_whenKeywordNotFound_throws() {
        given(keywordRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> keywordService.getGraph(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KEYWORD_NOT_FOUND);
        verifyNoInteractions(keywordGraphQueryService);
    }

    @Test
    void getTop_returnsKeywordWithMostArticles() {
        KeywordArticleCount top = new KeywordArticleCount(3L, "MySQL", 5L);
        given(keywordGraphQueryService.findTop()).willReturn(Optional.of(top));

        assertThat(keywordService.getTop()).isEqualTo(top);
    }

    @Test
    void getTop_whenNoKeywordHasArticles_throws() {
        given(keywordGraphQueryService.findTop()).willReturn(Optional.empty());

        assertThatThrownBy(() -> keywordService.getTop())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KEYWORD_NOT_FOUND);
    }
}
