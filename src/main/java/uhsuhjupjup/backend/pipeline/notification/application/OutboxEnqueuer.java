package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OutboxEnqueuer {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationSaver notificationSaver;

    @Transactional
    public int enqueueForMember(String email, int articleCount, String subject, String html,
                                String unsubscribeUrl, Long memberId, Map<Long, String> matchedKeywordsByArticle) {
        notificationOutboxRepository.save(NotificationOutbox.pending(
                email, RecipientType.MEMBER, articleCount, subject, html, unsubscribeUrl, LocalDateTime.now()));
        return notificationSaver.record(memberId, matchedKeywordsByArticle);
    }

    @Transactional
    public int enqueueForEmailSubscriber(String email, int articleCount, String subject, String html,
                                         String unsubscribeUrl, Long emailSubscriberId,
                                         Map<Long, String> matchedKeywordsByArticle) {
        notificationOutboxRepository.save(NotificationOutbox.pending(
                email, RecipientType.EMAIL_SUBSCRIBER, articleCount, subject, html, unsubscribeUrl,
                LocalDateTime.now()));
        return notificationSaver.recordEmail(emailSubscriberId, matchedKeywordsByArticle);
    }
}
