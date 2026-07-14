package uhsuhjupjup.backend.subscription.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import uhsuhjupjup.backend.config.JpaAuditingConfig;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.subscription.domain.KeywordSubscription;
import uhsuhjupjup.backend.subscription.domain.TopicSubscription;
import uhsuhjupjup.backend.support.MySqlTestSupport;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class SubscriptionRepositoryTest extends MySqlTestSupport {

    @Autowired
    private TopicSubscriptionRepository topicSubscriptionRepository;
    @Autowired
    private KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private KeywordRepository keywordRepository;

    private Member member;
    private Topic database;
    private Keyword kafka;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.create("github", "uid-1", "sub@example.com"));
        Topic backend = topicRepository.save(Topic.create("Backend"));
        database = topicRepository.save(Topic.create("Database"));
        kafka = keywordRepository.save(Keyword.create("Kafka"));

        topicSubscriptionRepository.save(TopicSubscription.of(member, database));
        topicSubscriptionRepository.save(TopicSubscription.of(member, backend));
        keywordSubscriptionRepository.save(KeywordSubscription.of(member, kafka));
    }

    @Test
    void findSubscribedTopics_orderedByName() {
        assertThat(topicSubscriptionRepository.findSubscribedTopics(member.getId()))
                .extracting(Topic::getName)
                .containsExactly("Backend", "Database");
    }

    @Test
    void deleteByMemberIdAndTopicIdIn_removesGivenTopics() {
        topicSubscriptionRepository.deleteByMemberIdAndTopicIdIn(member.getId(), List.of(database.getId()));

        assertThat(topicSubscriptionRepository.findSubscribedTopics(member.getId()))
                .extracting(Topic::getName)
                .containsExactly("Backend");
    }

    @Test
    void deleteByMemberId_removesAll() {
        topicSubscriptionRepository.deleteByMemberId(member.getId());
        keywordSubscriptionRepository.deleteByMemberId(member.getId());

        assertThat(topicSubscriptionRepository.findSubscribedTopics(member.getId())).isEmpty();
        assertThat(keywordSubscriptionRepository.findSubscribedKeywords(member.getId())).isEmpty();
    }
}
