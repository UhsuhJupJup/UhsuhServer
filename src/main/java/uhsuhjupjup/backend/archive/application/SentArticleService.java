package uhsuhjupjup.backend.archive.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.archive.application.dto.SentArticleResult;
import uhsuhjupjup.backend.archive.application.dto.SentArticlesResult;
import uhsuhjupjup.backend.article.application.dto.ArticleSummaryResult;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.pipeline.notification.domain.Notification;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SentArticleService {

    private static final int MAX_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final ArticleKeywordRepository articleKeywordRepository;

    public SentArticlesResult getSentArticles(Long memberId) {
        List<Notification> notifications = notificationRepository
                .findRecentWithArticleByMemberId(memberId, PageRequest.of(0, MAX_SIZE));

        Map<Long, List<String>> keywordsByArticle = keywordsByArticle(notifications);

        List<SentArticleResult> content = notifications.stream()
                .map(notification -> new SentArticleResult(
                        new ArticleSummaryResult(
                                notification.getArticle(),
                                keywordsByArticle.getOrDefault(notification.getArticle().getId(), List.of())),
                        notification.getSentAt()))
                .toList();

        return new SentArticlesResult(content);
    }

    private Map<Long, List<String>> keywordsByArticle(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return Map.of();
        }
        List<Long> articleIds = notifications.stream()
                .map(notification -> notification.getArticle().getId())
                .toList();
        return articleKeywordRepository.findWithKeywordByArticleIdIn(articleIds).stream()
                .collect(Collectors.groupingBy(
                        articleKeyword -> articleKeyword.getArticle().getId(),
                        Collectors.mapping(articleKeyword -> articleKeyword.getKeyword().getName(),
                                Collectors.toList())));
    }
}
