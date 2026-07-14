package uhsuhjupjup.backend.subscription.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.subscription.application.dto.SubscriptionsResult;
import uhsuhjupjup.backend.subscription.infra.KeywordSubscriptionRepository;
import uhsuhjupjup.backend.subscription.infra.TopicSubscriptionRepository;
import uhsuhjupjup.backend.support.KeywordFixture;
import uhsuhjupjup.backend.support.MemberFixture;
import uhsuhjupjup.backend.support.TopicFixture;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private TopicSubscriptionRepository topicSubscriptionRepository;
    @Mock
    private KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private uhsuhjupjup.backend.keyword.infra.KeywordRepository keywordRepository;
    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private final Member consented = MemberFixture.consentedMember(
            1L, "octocat@github.com", LocalDateTime.of(2026, 6, 25, 12, 0));

    @Test
    void getMySubscriptions_returnsTopicsAndKeywords() {
        given(topicSubscriptionRepository.findSubscribedTopics(1L))
                .willReturn(List.of(TopicFixture.topic(1L, "Database")));
        given(keywordSubscriptionRepository.findSubscribedKeywords(1L))
                .willReturn(List.of(KeywordFixture.keyword(11L, "Kafka")));

        SubscriptionsResult result = subscriptionService.getMySubscriptions(1L);

        assertThat(result.topics()).extracting(Topic::getName).containsExactly("Database");
        assertThat(result.keywords()).extracting(Keyword::getName).containsExactly("Kafka");
    }

    @Test
    void replaceSubscriptions_whenNotConsented_throwsConsentRequired() {
        Member notConsented = MemberFixture.member(2L, "no-consent@github.com");

        assertThatThrownBy(() -> subscriptionService.replaceSubscriptions(notConsented, List.of(1L), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONSENT_REQUIRED);
    }

    @Test
    void replaceSubscriptions_whenTopicMissing_throws() {
        given(topicRepository.findAllById(any())).willReturn(List.of());

        assertThatThrownBy(() -> subscriptionService.replaceSubscriptions(consented, List.of(9L), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOPIC_NOT_FOUND);
    }

    @Test
    void unsubscribeByToken_whenValid_deletesAllSubscriptions() {
        given(memberRepository.findByUnsubscribeToken("tok")).willReturn(Optional.of(consented));

        subscriptionService.unsubscribeByToken("tok");

        verify(topicSubscriptionRepository).deleteByMemberId(1L);
        verify(keywordSubscriptionRepository).deleteByMemberId(1L);
    }

    @Test
    void unsubscribeByToken_whenInvalid_throws() {
        given(memberRepository.findByUnsubscribeToken("bad")).willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.unsubscribeByToken("bad"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_UNSUBSCRIBE_TOKEN);
    }
}
