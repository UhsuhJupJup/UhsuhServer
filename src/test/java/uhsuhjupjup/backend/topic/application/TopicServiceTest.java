package uhsuhjupjup.backend.topic.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.support.KeywordFixture;
import uhsuhjupjup.backend.support.TopicFixture;
import uhsuhjupjup.backend.topic.application.dto.TopicDetailResult;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.domain.TopicKeyword;
import uhsuhjupjup.backend.topic.infra.TopicKeywordRepository;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private TopicKeywordRepository topicKeywordRepository;

    @InjectMocks
    private TopicService topicService;

    @Test
    void findAll_returnsAllOrdered() {
        List<Topic> topics = List.of(TopicFixture.topic(1L, "Database"), TopicFixture.topic(2L, "Backend"));
        given(topicRepository.findAllByOrderByIdAsc()).willReturn(topics);

        assertThat(topicService.findAll()).isEqualTo(topics);
    }

    @Test
    void getDetail_returnsTopicWithKeywords() {
        Topic topic = TopicFixture.topic(1L, "Database");
        Keyword mysql = KeywordFixture.keyword(3L, "MySQL");
        Keyword redis = KeywordFixture.keyword(1L, "Redis");
        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicKeywordRepository.findWithKeywordByTopicId(1L))
                .willReturn(List.of(TopicKeyword.of(topic, mysql), TopicKeyword.of(topic, redis)));

        TopicDetailResult result = topicService.getDetail(1L);

        assertThat(result.topic()).isEqualTo(topic);
        assertThat(result.keywords()).containsExactly(mysql, redis);
    }

    @Test
    void getDetail_whenTopicNotFound_throws() {
        given(topicRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> topicService.getDetail(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOPIC_NOT_FOUND);
    }

    @Test
    void findAllWithKeywords_groupsKeywordsPerTopic() {
        Topic database = TopicFixture.topic(1L, "Database");
        Topic backend = TopicFixture.topic(2L, "Backend");
        Keyword mysql = KeywordFixture.keyword(3L, "MySQL");
        Keyword redis = KeywordFixture.keyword(1L, "Redis");
        Keyword spring = KeywordFixture.keyword(5L, "Spring");
        given(topicRepository.findAllByOrderByIdAsc()).willReturn(List.of(database, backend));
        given(topicKeywordRepository.findAllWithTopicAndKeyword()).willReturn(List.of(
                TopicKeyword.of(database, mysql), TopicKeyword.of(database, redis),
                TopicKeyword.of(backend, spring)));

        List<TopicDetailResult> result = topicService.findAllWithKeywords();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).topic()).isEqualTo(database);
        assertThat(result.get(0).keywords()).containsExactly(mysql, redis);
        assertThat(result.get(1).topic()).isEqualTo(backend);
        assertThat(result.get(1).keywords()).containsExactly(spring);
    }

    @Test
    void findAllWithKeywords_topicWithNoKeywords_returnsEmptyList() {
        Topic empty = TopicFixture.topic(9L, "Empty");
        given(topicRepository.findAllByOrderByIdAsc()).willReturn(List.of(empty));
        given(topicKeywordRepository.findAllWithTopicAndKeyword()).willReturn(List.of());

        List<TopicDetailResult> result = topicService.findAllWithKeywords();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).keywords()).isEmpty();
    }
}
