package uhseojupjup.backend.topic.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhseojupjup.backend.topic.domain.TopicKeyword;

import java.util.List;

public interface TopicKeywordRepository extends JpaRepository<TopicKeyword, Long> {

    List<TopicKeyword> findByTopicId(Long topicId);

    List<TopicKeyword> findByKeywordId(Long keywordId);
}
