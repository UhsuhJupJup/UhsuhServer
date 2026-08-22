package uhsuhjupjup.backend.emailsubscription.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationRepository;
import uhsuhjupjup.backend.subscription.domain.KeywordSubscription;
import uhsuhjupjup.backend.subscription.infra.KeywordSubscriptionRepository;

@Service
@RequiredArgsConstructor
public class EmailSubscriptionClaimService {

    private final EmailSubscriberRepository emailSubscriberRepository;
    private final EmailSubscriptionRepository emailSubscriptionRepository;
    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final NotificationRepository notificationRepository;

    /**
     * 새 회원과 같은 이메일의 비회원 구독자가 있으면 흡수한다(한 트랜잭션):
     * 알림은 회원으로 re-key(복사 아님), 구독은 keyword_subscription으로 이동, 구독자는 삭제.
     * 로그인으로 회원이 새로 만들어지는 순간이라 회원 쪽엔 기존 알림·구독이 없어 충돌이 없다.
     */
    @Transactional
    public void claim(Member member) {
        emailSubscriberRepository.findByEmail(member.getEmail()).ifPresent(subscriber -> {
            Long subscriberId = subscriber.getId();
            // 삭제 전에 알림을 회원으로 re-key(먼저 하지 않으면 FK CASCADE로 함께 삭제됨)
            notificationRepository.reassignToMember(member, subscriberId);
            // 구독은 키워드만 읽어 회원 구독(keyword_subscription)으로 재생성
            emailSubscriptionRepository.findKeywordsByEmailSubscriberId(subscriberId)
                    .forEach(keyword -> keywordSubscriptionRepository.save(KeywordSubscription.of(member, keyword)));
            // 비회원 구독·구독자 삭제(관리 엔티티가 삭제될 구독자를 참조하지 않도록 명시 삭제)
            emailSubscriptionRepository.deleteByEmailSubscriberId(subscriberId);
            emailSubscriberRepository.delete(subscriber);
        });
    }
}
