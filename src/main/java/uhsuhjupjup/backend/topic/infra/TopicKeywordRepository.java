package uhsuhjupjup.backend.topic.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.topic.domain.TopicKeyword;

import java.util.List;

public interface TopicKeywordRepository extends JpaRepository<TopicKeyword, Long> {

    @Query("select tk from TopicKeyword tk join fetch tk.keyword where tk.topic.id = :topicId order by tk.keyword.name")
    List<TopicKeyword> findWithKeywordByTopicId(Long topicId);

    @Query("select tk from TopicKeyword tk join fetch tk.topic where tk.keyword.id = :keywordId order by tk.topic.id")
    List<TopicKeyword> findWithTopicByKeywordId(Long keywordId);
}
