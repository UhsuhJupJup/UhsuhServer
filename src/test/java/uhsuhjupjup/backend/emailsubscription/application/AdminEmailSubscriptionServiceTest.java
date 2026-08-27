package uhsuhjupjup.backend.emailsubscription.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscription;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.emailsubscription.ui.dto.AdminEmailSubscriberResponse;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.subscription.domain.KeywordSubscription;
import uhsuhjupjup.backend.subscription.infra.KeywordSubscriptionRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminEmailSubscriptionServiceTest {

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;
    @Mock
    private EmailSubscriptionRepository emailSubscriptionRepository;
    @Mock
    private KeywordSubscriptionRepository keywordSubscriptionRepository;

    @InjectMocks
    private AdminEmailSubscriptionService adminEmailSubscriptionService;

    @Test
    void 비회원_구독자와_키워드를_묶어_반환한다() {
        EmailSubscriber verified = subscriber(1L, "a@example.com", true, LocalDateTime.now());
        EmailSubscriber pending = subscriber(2L, "b@example.com", false, LocalDateTime.now().minusDays(1));
        given(emailSubscriberRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(verified, pending));
        given(emailSubscriptionRepository.findWithKeywordByEmailSubscriberIdIn(List.of(1L, 2L)))
                .willReturn(List.of(
                        EmailSubscription.of(verified, Keyword.create("Redis")),
                        EmailSubscription.of(verified, Keyword.create("JPA"))));

        List<AdminEmailSubscriberResponse> result = adminEmailSubscriptionService.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).email()).isEqualTo("a@example.com");
        assertThat(result.get(0).recipientType()).isEqualTo("EMAIL_SUBSCRIBER");
        assertThat(result.get(0).verified()).isTrue();
        assertThat(result.get(0).keywords()).containsExactlyInAnyOrder("Redis", "JPA");
        assertThat(result.get(1).email()).isEqualTo("b@example.com");
        assertThat(result.get(1).verified()).isFalse();
        assertThat(result.get(1).keywords()).isEmpty();
    }

    @Test
    void 회원_구독자를_포함한다() {
        Member member = member(10L, "member@example.com", LocalDateTime.now());
        given(keywordSubscriptionRepository.findAllWithMemberAndKeyword()).willReturn(List.of(
                KeywordSubscription.of(member, Keyword.create("Redis")),
                KeywordSubscription.of(member, Keyword.create("JPA"))));
        given(emailSubscriberRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of());

        List<AdminEmailSubscriberResponse> result = adminEmailSubscriptionService.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("member@example.com");
        assertThat(result.get(0).recipientType()).isEqualTo("MEMBER");
        assertThat(result.get(0).verified()).isTrue();
        assertThat(result.get(0).keywords()).containsExactlyInAnyOrder("Redis", "JPA");
    }

    @Test
    void 회원과_비회원을_등록일_내림차순으로_병합한다() {
        Member member = member(10L, "member@example.com", LocalDateTime.now().minusDays(2));
        given(keywordSubscriptionRepository.findAllWithMemberAndKeyword()).willReturn(List.of(
                KeywordSubscription.of(member, Keyword.create("Redis"))));
        EmailSubscriber recent = subscriber(1L, "recent@example.com", true, LocalDateTime.now());
        given(emailSubscriberRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(recent));
        given(emailSubscriptionRepository.findWithKeywordByEmailSubscriberIdIn(List.of(1L))).willReturn(List.of());

        List<AdminEmailSubscriberResponse> result = adminEmailSubscriptionService.list();

        assertThat(result).extracting(AdminEmailSubscriberResponse::email)
                .containsExactly("recent@example.com", "member@example.com");
        assertThat(result).extracting(AdminEmailSubscriberResponse::recipientType)
                .containsExactly("EMAIL_SUBSCRIBER", "MEMBER");
    }

    @Test
    void 구독이_없으면_빈_리스트() {
        given(emailSubscriberRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of());

        assertThat(adminEmailSubscriptionService.list()).isEmpty();
    }

    private EmailSubscriber subscriber(Long id, String email, boolean verified, LocalDateTime createdAt) {
        EmailSubscriber subscriber = EmailSubscriber.create(email);
        ReflectionTestUtils.setField(subscriber, "id", id);
        ReflectionTestUtils.setField(subscriber, "createdAt", createdAt);
        if (verified) {
            subscriber.verify(LocalDateTime.now());
        }
        return subscriber;
    }

    private Member member(Long id, String email, LocalDateTime createdAt) {
        Member member = Member.create("google", "uid" + id, email);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", createdAt);
        return member;
    }
}
