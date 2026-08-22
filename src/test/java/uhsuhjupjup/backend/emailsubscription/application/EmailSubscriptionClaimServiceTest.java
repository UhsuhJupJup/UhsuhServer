package uhsuhjupjup.backend.emailsubscription.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationRepository;
import uhsuhjupjup.backend.subscription.domain.KeywordSubscription;
import uhsuhjupjup.backend.subscription.infra.KeywordSubscriptionRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSubscriptionClaimServiceTest {

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;
    @Mock
    private EmailSubscriptionRepository emailSubscriptionRepository;
    @Mock
    private KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private EmailSubscriptionClaimService claimService;

    @Test
    void 같은_이메일_비회원이_있으면_알림rekey_구독이동_구독자삭제() {
        Member member = Member.create("google", "uid", "c@example.com");
        EmailSubscriber subscriber = EmailSubscriber.create("c@example.com");
        ReflectionTestUtils.setField(subscriber, "id", 9L);
        given(emailSubscriberRepository.findByEmail("c@example.com")).willReturn(Optional.of(subscriber));
        given(emailSubscriptionRepository.findKeywordsByEmailSubscriberId(9L))
                .willReturn(List.of(Keyword.create("Redis")));

        claimService.claim(member);

        verify(notificationRepository).reassignToMember(member, 9L);
        verify(keywordSubscriptionRepository).save(any(KeywordSubscription.class));
        verify(emailSubscriptionRepository).deleteByEmailSubscriberId(9L);
        verify(emailSubscriberRepository).delete(subscriber);
    }

    @Test
    void 같은_이메일_비회원이_없으면_아무것도_안한다() {
        Member member = Member.create("google", "uid", "none@example.com");
        given(emailSubscriberRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

        claimService.claim(member);

        verify(notificationRepository, never()).reassignToMember(any(), any());
        verify(keywordSubscriptionRepository, never()).save(any());
        verify(emailSubscriberRepository, never()).delete(any());
    }
}
