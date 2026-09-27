package uhsuhjupjup.backend.techblog.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.techblog.article.infra.ArticleRepository;
import uhsuhjupjup.backend.techblog.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.techblog.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.techblog.pipeline.notification.domain.Notification;
import uhsuhjupjup.backend.techblog.pipeline.notification.infra.NotificationRepository;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationSaver {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final ArticleRepository articleRepository;
    private final EmailSubscriberRepository emailSubscriberRepository;

    @Transactional
    public int record(Long memberId, Map<Long, String> matchedKeywordsByArticle) {
        if (matchedKeywordsByArticle.isEmpty()) {
            return 0;
        }
        Member memberRef = memberRepository.getReferenceById(memberId);
        List<Notification> rows = matchedKeywordsByArticle.entrySet().stream()
                .map(entry -> Notification.of(memberRef,
                        articleRepository.getReferenceById(entry.getKey()), entry.getValue()))
                .toList();
        notificationRepository.saveAll(rows);
        return rows.size();
    }

    @Transactional
    public int recordEmail(Long emailSubscriberId, Map<Long, String> matchedKeywordsByArticle) {
        if (matchedKeywordsByArticle.isEmpty()) {
            return 0;
        }
        EmailSubscriber subscriberRef = emailSubscriberRepository.getReferenceById(emailSubscriberId);
        List<Notification> rows = matchedKeywordsByArticle.entrySet().stream()
                .map(entry -> Notification.ofEmail(subscriberRef,
                        articleRepository.getReferenceById(entry.getKey()), entry.getValue()))
                .toList();
        notificationRepository.saveAll(rows);
        return rows.size();
    }
}
