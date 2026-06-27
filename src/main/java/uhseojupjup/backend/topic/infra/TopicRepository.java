package uhseojupjup.backend.topic.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhseojupjup.backend.topic.domain.Topic;

import java.util.List;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findAllByOrderByIdAsc();
}
