package uhsuhjupjup.backend.topic.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;
import uhsuhjupjup.backend.topic.domain.Topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
class TopicRepositoryTest {

    @Autowired
    private TopicRepository topicRepository;

    @Test
    void findAllByOrderByIdAsc_ordersById() {
        topicRepository.save(Topic.create("Database"));
        topicRepository.save(Topic.create("Backend"));

        assertThat(topicRepository.findAllByOrderByIdAsc())
                .extracting(Topic::getName)
                .containsExactly("Database", "Backend");
    }

    @Test
    void duplicateName_violatesUniqueConstraint() {
        topicRepository.saveAndFlush(Topic.create("Database"));

        assertThatThrownBy(() -> topicRepository.saveAndFlush(Topic.create("Database")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditing_setsCreatedAndUpdatedAt() {
        Topic saved = topicRepository.saveAndFlush(Topic.create("Database"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
