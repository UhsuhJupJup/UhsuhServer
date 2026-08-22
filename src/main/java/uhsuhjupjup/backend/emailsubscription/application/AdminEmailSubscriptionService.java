package uhsuhjupjup.backend.emailsubscription.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.emailsubscription.ui.dto.AdminEmailSubscriberResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEmailSubscriptionService {

    private final EmailSubscriberRepository emailSubscriberRepository;
    private final EmailSubscriptionRepository emailSubscriptionRepository;

    public List<AdminEmailSubscriberResponse> list() {
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
                        subscriber.isVerified(),
                        keywordsBySubscriber.getOrDefault(subscriber.getId(), List.of()),
                        subscriber.getCreatedAt()))
                .toList();
    }
}
