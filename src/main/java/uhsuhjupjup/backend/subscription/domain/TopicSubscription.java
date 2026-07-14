package uhsuhjupjup.backend.subscription.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.common.domain.BaseEntity;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.topic.domain.Topic;

@Entity
@Table(name = "topic_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopicSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    private TopicSubscription(Member member, Topic topic) {
        this.member = member;
        this.topic = topic;
    }

    public static TopicSubscription of(Member member, Topic topic) {
        return new TopicSubscription(member, topic);
    }
}
