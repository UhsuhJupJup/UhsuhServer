package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.notification.application.dto.DigestArticleView;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailRecipientPair;
import uhsuhjupjup.backend.pipeline.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.pipeline.notification.application.dto.RecipientPair;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int WINDOW_DAYS = 2;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final NotificationRepository notificationRepository;
    private final ArticleRepository articleRepository;
    private final ArticleKeywordRepository articleKeywordRepository;
    private final MemberRepository memberRepository;
    private final EmailSubscriberRepository emailSubscriberRepository;
    private final DigestRenderer digestRenderer;
    private final EmailSender emailSender;
    private final NotificationSaver notificationSaver;
    private final EmailSendLogService emailSendLogService;

    @Value("${notification.max-per-member:5}")
    private int maxPerMember;

    public NotificationResult notifyRecent() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(WINDOW_DAYS);
        Map<Long, Set<Long>> articlesByMember = fanOutMembers(threshold);
        Map<Long, Set<Long>> articlesByEmailSubscriber = fanOutEmailSubscribers(threshold);
        if (articlesByMember.isEmpty() && articlesByEmailSubscriber.isEmpty()) {
            return logged(new NotificationResult(0, 0, 0, 0));
        }

        Set<Long> articleIds = new HashSet<>();
        articlesByMember.values().forEach(articleIds::addAll);
        articlesByEmailSubscriber.values().forEach(articleIds::addAll);

        Map<Long, Article> articleById = articleRepository.findWithBlogByIdIn(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, article -> article));
        Map<Long, List<String>> keywordsByArticle = articleKeywordRepository.findWithKeywordByArticleIdIn(articleIds)
                .stream().collect(Collectors.groupingBy(ak -> ak.getArticle().getId(),
                        Collectors.mapping(ak -> ak.getKeyword().getName(), Collectors.toList())));
        String digestDate = LocalDateTime.now().format(DATE);

        SendOutcome members = sendToMembers(articlesByMember, articleById, keywordsByArticle, digestDate);
        SendOutcome emails = sendToEmailSubscribers(articlesByEmailSubscriber, articleById, keywordsByArticle, digestDate);

        return logged(new NotificationResult(members.notified(), emails.notified(),
                members.recorded() + emails.recorded(), members.failed() + emails.failed()));
    }

    private SendOutcome sendToMembers(Map<Long, Set<Long>> articlesByMember, Map<Long, Article> articleById,
                                      Map<Long, List<String>> keywordsByArticle, String digestDate) {
        if (articlesByMember.isEmpty()) {
            return SendOutcome.EMPTY;
        }
        Map<Long, Member> memberById = memberRepository.findAllById(articlesByMember.keySet()).stream()
                .collect(Collectors.toMap(Member::getId, member -> member));
        int notified = 0;
        int recorded = 0;
        int failed = 0;
        for (Map.Entry<Long, Set<Long>> entry : articlesByMember.entrySet()) {
            Member member = memberById.get(entry.getKey());
            List<Long> orderedArticleIds = pickRecentByCollected(entry.getValue(), articleById);
            if (member == null || orderedArticleIds.isEmpty()) {
                continue;
            }
            try {
                List<DigestArticleView> views = buildViews(orderedArticleIds, articleById, keywordsByArticle);
                String html = digestRenderer.render(member, views, digestDate);
                String subject = subject(views.size());
                emailSender.send(new EmailMessage(member.getEmail(), subject, html,
                        digestRenderer.unsubscribeUrl(member)));
                emailSendLogService.record(member.getEmail(), RecipientType.MEMBER, views.size(), subject);
                recorded += notificationSaver.record(member.getId(),
                        matchedKeywordsByArticle(orderedArticleIds, keywordsByArticle));
                notified++;
            } catch (Exception e) {
                failed++;
                log.warn("다이제스트 발송 실패 memberId={} 사유={}", member.getId(), e.getMessage());
            }
        }
        return new SendOutcome(notified, recorded, failed);
    }

    private SendOutcome sendToEmailSubscribers(Map<Long, Set<Long>> articlesBySubscriber, Map<Long, Article> articleById,
                                               Map<Long, List<String>> keywordsByArticle, String digestDate) {
        if (articlesBySubscriber.isEmpty()) {
            return SendOutcome.EMPTY;
        }
        Map<Long, EmailSubscriber> subscriberById = emailSubscriberRepository.findAllById(articlesBySubscriber.keySet())
                .stream().collect(Collectors.toMap(EmailSubscriber::getId, subscriber -> subscriber));
        int notified = 0;
        int recorded = 0;
        int failed = 0;
        for (Map.Entry<Long, Set<Long>> entry : articlesBySubscriber.entrySet()) {
            EmailSubscriber subscriber = subscriberById.get(entry.getKey());
            List<Long> orderedArticleIds = pickRecentByCollected(entry.getValue(), articleById);
            if (subscriber == null || orderedArticleIds.isEmpty()) {
                continue;
            }
            try {
                List<DigestArticleView> views = buildViews(orderedArticleIds, articleById, keywordsByArticle);
                String unsubscribeUrl = digestRenderer.unsubscribeUrl(subscriber.getUnsubscribeToken());
                String subject = subject(views.size());
                String html = digestRenderer.render(subscriber.getEmail(), views, digestDate, unsubscribeUrl);
                emailSender.send(new EmailMessage(subscriber.getEmail(), subject, html, unsubscribeUrl));
                emailSendLogService.record(subscriber.getEmail(), RecipientType.EMAIL_SUBSCRIBER, views.size(), subject);
                recorded += notificationSaver.recordEmail(subscriber.getId(),
                        matchedKeywordsByArticle(orderedArticleIds, keywordsByArticle));
                notified++;
            } catch (Exception e) {
                failed++;
                log.warn("비회원 다이제스트 발송 실패 emailSubscriberId={} 사유={}", subscriber.getId(), e.getMessage());
            }
        }
        return new SendOutcome(notified, recorded, failed);
    }

    private Map<Long, Set<Long>> fanOutMembers(LocalDateTime threshold) {
        return notificationRepository.findKeywordPathRecipients(threshold).stream()
                .collect(Collectors.groupingBy(RecipientPair::memberId,
                        Collectors.mapping(RecipientPair::articleId, Collectors.toCollection(LinkedHashSet::new))));
    }

    private Map<Long, Set<Long>> fanOutEmailSubscribers(LocalDateTime threshold) {
        return notificationRepository.findKeywordPathEmailRecipients(threshold).stream()
                .collect(Collectors.groupingBy(EmailRecipientPair::emailSubscriberId,
                        Collectors.mapping(EmailRecipientPair::articleId, Collectors.toCollection(LinkedHashSet::new))));
    }

    private List<DigestArticleView> buildViews(List<Long> orderedArticleIds, Map<Long, Article> articleById,
                                               Map<Long, List<String>> keywordsByArticle) {
        return orderedArticleIds.stream()
                .map(id -> toView(articleById.get(id), keywordsByArticle.getOrDefault(id, List.of())))
                .toList();
    }

    private String subject(int count) {
        return "🧹 오늘 주워온 글 " + count + "개";
    }

    private List<Long> pickRecentByCollected(Set<Long> articleIds, Map<Long, Article> articleById) {
        return articleIds.stream()
                .filter(articleById::containsKey)
                .sorted(Comparator.comparing((Long id) -> articleById.get(id).getCollectedAt()).reversed()
                        .thenComparing(Comparator.reverseOrder()))
                .limit(maxPerMember)
                .toList();
    }

    private Map<Long, String> matchedKeywordsByArticle(List<Long> articleIds, Map<Long, List<String>> keywordsByArticle) {
        Map<Long, String> matched = new LinkedHashMap<>();
        for (Long id : articleIds) {
            matched.put(id, String.join(", ", keywordsByArticle.getOrDefault(id, List.of())));
        }
        return matched;
    }

    private DigestArticleView toView(Article article, List<String> keywords) {
        return new DigestArticleView(
                article.getBlog().getName(),
                article.getPublishedAt().format(DATE),
                article.getTitle(),
                article.getUrl(),
                keywords);
    }

    private NotificationResult logged(NotificationResult result) {
        log.info("발송 완료 {}", result);
        return result;
    }

    private record SendOutcome(int notified, int recorded, int failed) {
        private static final SendOutcome EMPTY = new SendOutcome(0, 0, 0);
    }
}
