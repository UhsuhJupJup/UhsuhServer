package uhsuhjupjup.backend.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.notification.application.dto.DigestArticleView;
import uhsuhjupjup.backend.notification.application.dto.EmailMessage;
import uhsuhjupjup.backend.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.notification.application.dto.RecipientPair;
import uhsuhjupjup.backend.notification.infra.NotificationRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final DigestRenderer digestRenderer;
    private final EmailSender emailSender;
    private final NotificationSaver notificationSaver;

    public NotificationResult notifyRecent() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(WINDOW_DAYS);
        Map<Long, Set<Long>> articlesByMember = fanOut(threshold);
        if (articlesByMember.isEmpty()) {
            return logged(new NotificationResult(0, 0, 0));
        }

        Set<Long> articleIds = articlesByMember.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet());
        Map<Long, Article> articleById = articleRepository.findWithBlogByIdIn(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, article -> article));
        Map<Long, List<String>> keywordsByArticle = articleKeywordRepository.findWithKeywordByArticleIdIn(articleIds)
                .stream().collect(Collectors.groupingBy(ak -> ak.getArticle().getId(),
                        Collectors.mapping(ak -> ak.getKeyword().getName(), Collectors.toList())));
        Map<Long, Member> memberById = memberRepository.findAllById(articlesByMember.keySet()).stream()
                .collect(Collectors.toMap(Member::getId, member -> member));

        String digestDate = LocalDateTime.now().format(DATE);
        int membersNotified = 0;
        int notificationsRecorded = 0;
        int failedMembers = 0;
        for (Map.Entry<Long, Set<Long>> entry : articlesByMember.entrySet()) {
            Member member = memberById.get(entry.getKey());
            List<Long> orderedArticleIds = orderByPublishedDesc(entry.getValue(), articleById);
            if (member == null || orderedArticleIds.isEmpty()) {
                continue;
            }
            try {
                List<DigestArticleView> views = orderedArticleIds.stream()
                        .map(id -> toView(articleById.get(id), keywordsByArticle.getOrDefault(id, List.of())))
                        .toList();
                String html = digestRenderer.render(member, views, digestDate);
                emailSender.send(new EmailMessage(member.getEmail(),
                        "🧹 오늘 주워온 글 " + views.size() + "개", html));
                notificationsRecorded += notificationSaver.record(member.getId(),
                        matchedKeywordsByArticle(orderedArticleIds, keywordsByArticle));
                membersNotified++;
            } catch (Exception e) {
                failedMembers++;
                log.warn("다이제스트 발송 실패 memberId={} 사유={}", member.getId(), e.getMessage());
            }
        }
        return logged(new NotificationResult(membersNotified, notificationsRecorded, failedMembers));
    }

    private Map<Long, Set<Long>> fanOut(LocalDateTime threshold) {
        List<RecipientPair> pairs = new ArrayList<>(notificationRepository.findKeywordPathRecipients(threshold));
        pairs.addAll(notificationRepository.findTopicPathRecipients(threshold));
        return pairs.stream().collect(Collectors.groupingBy(
                RecipientPair::memberId,
                Collectors.mapping(RecipientPair::articleId, Collectors.toCollection(LinkedHashSet::new))));
    }

    private List<Long> orderByPublishedDesc(Set<Long> articleIds, Map<Long, Article> articleById) {
        return articleIds.stream()
                .filter(articleById::containsKey)
                .sorted(Comparator.comparing((Long id) -> articleById.get(id).getPublishedAt()).reversed())
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
}
