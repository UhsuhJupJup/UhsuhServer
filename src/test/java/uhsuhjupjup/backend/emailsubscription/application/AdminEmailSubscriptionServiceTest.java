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

    @InjectMocks
    private AdminEmailSubscriptionService adminEmailSubscriptionService;

    @Test
    void 구독자와_키워드를_묶어_반환한다() {
        EmailSubscriber verified = subscriber(1L, "a@example.com", true);
        EmailSubscriber pending = subscriber(2L, "b@example.com", false);
        given(emailSubscriberRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(verified, pending));
        given(emailSubscriptionRepository.findWithKeywordByEmailSubscriberIdIn(List.of(1L, 2L)))
                .willReturn(List.of(
                        EmailSubscription.of(verified, Keyword.create("Redis")),
                        EmailSubscription.of(verified, Keyword.create("JPA"))));

        List<AdminEmailSubscriberResponse> result = adminEmailSubscriptionService.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).email()).isEqualTo("a@example.com");
        assertThat(result.get(0).verified()).isTrue();
        assertThat(result.get(0).keywords()).containsExactlyInAnyOrder("Redis", "JPA");
        assertThat(result.get(1).email()).isEqualTo("b@example.com");
        assertThat(result.get(1).verified()).isFalse();
        assertThat(result.get(1).keywords()).isEmpty();
    }

    @Test
    void 구독자가_없으면_빈_리스트() {
        given(emailSubscriberRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of());

        assertThat(adminEmailSubscriptionService.list()).isEmpty();
    }

    private EmailSubscriber subscriber(Long id, String email, boolean verified) {
        EmailSubscriber subscriber = EmailSubscriber.create(email);
        ReflectionTestUtils.setField(subscriber, "id", id);
        if (verified) {
            subscriber.verify(LocalDateTime.now());
        }
        return subscriber;
    }
}
