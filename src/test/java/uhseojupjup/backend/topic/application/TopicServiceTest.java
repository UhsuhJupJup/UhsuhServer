package uhseojupjup.backend.topic.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhseojupjup.backend.support.TopicFixture;
import uhseojupjup.backend.topic.domain.Topic;
import uhseojupjup.backend.topic.infra.TopicRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Mock
    private TopicRepository topicRepository;

    @InjectMocks
    private TopicService topicService;

    @Test
    void findAll_returnsAllOrdered() {
        List<Topic> topics = List.of(TopicFixture.topic(1L, "Database"), TopicFixture.topic(2L, "Backend"));
        given(topicRepository.findAllByOrderByIdAsc()).willReturn(topics);

        assertThat(topicService.findAll()).isEqualTo(topics);
    }
}
