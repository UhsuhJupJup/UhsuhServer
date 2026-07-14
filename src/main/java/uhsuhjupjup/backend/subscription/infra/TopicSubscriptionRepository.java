package uhsuhjupjup.backend.subscription.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.subscription.domain.TopicSubscription;
import uhsuhjupjup.backend.topic.domain.Topic;

import java.util.Collection;
import java.util.List;

public interface TopicSubscriptionRepository extends JpaRepository<TopicSubscription, Long> {

    @Query("select ts.topic from TopicSubscription ts where ts.member.id = :memberId order by ts.topic.name")
    List<Topic> findSubscribedTopics(Long memberId);

    void deleteByMemberIdAndTopicIdIn(Long memberId, Collection<Long> topicIds);

    void deleteByMemberId(Long memberId);
}
