package uhseojupjup.backend.topic.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhseojupjup.backend.common.exception.BusinessException;
import uhseojupjup.backend.common.exception.ErrorCode;
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.support.KeywordFixture;
import uhseojupjup.backend.support.TopicFixture;
import uhseojupjup.backend.topic.domain.Topic;
import uhseojupjup.backend.topic.domain.TopicKeyword;
import uhseojupjup.backend.topic.infra.TopicKeywordRepository;
import uhseojupjup.backend.topic.infra.TopicRepository;

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
}
