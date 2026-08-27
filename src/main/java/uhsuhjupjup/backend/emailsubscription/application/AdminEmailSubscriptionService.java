package uhsuhjupjup.backend.emailsubscription.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.emailsubscription.ui.dto.AdminEmailSubscriberResponse;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.subscription.domain.KeywordSubscription;
import uhsuhjupjup.backend.subscription.infra.KeywordSubscriptionRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEmailSubscriptionService {

    private static final String TYPE_MEMBER = "MEMBER";
    private static final String TYPE_EMAIL_SUBSCRIBER = "EMAIL_SUBSCRIBER";

    private final EmailSubscriberRepository emailSubscriberRepository;
    private final EmailSubscriptionRepository emailSubscriptionRepository;
    private final KeywordSubscriptionRepository keywordSubscriptionRepository;

    public List<AdminEmailSubscriberResponse> list() {
        List<AdminEmailSubscriberResponse> merged = new ArrayList<>();
        merged.addAll(memberSubscribers());
        merged.addAll(emailSubscribers());
        merged.sort(Comparator.comparing(AdminEmailSubscriberResponse::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return merged;
    }

    private List<AdminEmailSubscriberResponse> memberSubscribers() {
        List<KeywordSubscription> subscriptions = keywordSubscriptionRepository.findAllWithMemberAndKeyword();
        Map<Long, Member> memberById = new LinkedHashMap<>();
        Map<Long, List<String>> keywordsByMemberId = new LinkedHashMap<>();
        for (KeywordSubscription subscription : subscriptions) {
            Member member = subscription.getMember();
            memberById.putIfAbsent(member.getId(), member);
            keywordsByMemberId.computeIfAbsent(member.getId(), id -> new ArrayList<>())
                    .add(subscription.getKeyword().getName());
        }
        return memberById.values().stream()
                .map(member -> new AdminEmailSubscriberResponse(
                        member.getId(),
                        member.getEmail(),
                        TYPE_MEMBER,
                        true,
                        keywordsByMemberId.getOrDefault(member.getId(), List.of()),
                        member.getCreatedAt()))
                .toList();
    }

    private List<AdminEmailSubscriberResponse> emailSubscribers() {
        List<EmailSubscriber> subscribers = emailSubscriberRepository.findAllByOrderByCreatedAtDesc();
        if (subscribers.isEmpty()) {
            return List.of();
        }
        List<Long> ids = subscribers.stream().map(EmailSubscriber::getId).toList();
        Map<Long, List<String>> keywordsBySubscriber = emailSubscriptionRepository
                .findWithKeywordByEmailSubscriberIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        subscription -> subscription.getEmailSubscriber().getId(),
                        Collectors.mapping(subscription -> subscription.getKeyword().getName(), Collectors.toList())));

        return subscribers.stream()
                .map(subscriber -> new AdminEmailSubscriberResponse(
                        subscriber.getId(),
                        subscriber.getEmail(),
                        TYPE_EMAIL_SUBSCRIBER,
                        subscriber.isVerified(),
                        keywordsBySubscriber.getOrDefault(subscriber.getId(), List.of()),
                        subscriber.getCreatedAt()))
                .toList();
    }
}
