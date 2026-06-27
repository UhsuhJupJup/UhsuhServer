package uhseojupjup.backend.keyword.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhseojupjup.backend.common.exception.BusinessException;
import uhseojupjup.backend.common.exception.ErrorCode;
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.keyword.infra.KeywordRepository;
import uhseojupjup.backend.support.KeywordFixture;
import uhseojupjup.backend.support.TopicFixture;
import uhseojupjup.backend.topic.domain.Topic;
import uhseojupjup.backend.topic.domain.TopicKeyword;
import uhseojupjup.backend.topic.infra.TopicKeywordRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KeywordServiceTest {

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private TopicKeywordRepository topicKeywordRepository;

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
    void getDetail_returnsKeywordWithTopics() {
        Keyword keyword = KeywordFixture.keyword(3L, "MySQL");
        Topic database = TopicFixture.topic(1L, "Database");
        given(keywordRepository.findById(3L)).willReturn(Optional.of(keyword));
        given(topicKeywordRepository.findWithTopicByKeywordId(3L))
                .willReturn(List.of(TopicKeyword.of(database, keyword)));

        KeywordDetailResult result = keywordService.getDetail(3L);

        assertThat(result.keyword()).isEqualTo(keyword);
        assertThat(result.topics()).containsExactly(database);
    }

    @Test
    void getDetail_whenKeywordNotFound_throws() {
        given(keywordRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> keywordService.getDetail(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KEYWORD_NOT_FOUND);
    }
}
