package uhsuhjupjup.backend.pipeline.notification.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uhsuhjupjup.backend.techblog.article.domain.Article;
import uhsuhjupjup.backend.techblog.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.techblog.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.techblog.article.infra.ArticleRepository;
import uhsuhjupjup.backend.techblog.blog.domain.Blog;
import uhsuhjupjup.backend.techblog.blog.infra.BlogRepository;
import uhsuhjupjup.backend.techblog.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.techblog.emailsubscription.domain.EmailSubscription;
import uhsuhjupjup.backend.techblog.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.techblog.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.techblog.keyword.domain.Keyword;
import uhsuhjupjup.backend.techblog.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailRecipientPair;
import uhsuhjupjup.backend.pipeline.notification.domain.Notification;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
class NotificationEmailRecipientQueryTest {

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private BlogRepository blogRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleKeywordRepository articleKeywordRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private EmailSubscriberRepository emailSubscriberRepository;
    @Autowired
    private EmailSubscriptionRepository emailSubscriptionRepository;

    private Article article;
    private EmailSubscriber verified;

    @BeforeEach
    void setUp() {
        Blog blog = blogRepository.save(
                Blog.create("우아한형제들", "techblog.woowahan.com", "https://techblog.woowahan.com/feed.xml"));
        Keyword redis = keywordRepository.save(Keyword.create("Redis"));
        article = articleRepository.save(
                Article.create(blog, "Redis 분산락", "https://ex/1", LocalDateTime.now().minusHours(1)));
        articleKeywordRepository.save(ArticleKeyword.of(article, redis, "title"));

        EmailSubscriber v = EmailSubscriber.create("v@example.com");
        v.verify(LocalDateTime.now());
        verified = emailSubscriberRepository.save(v);
        emailSubscriptionRepository.save(EmailSubscription.of(verified, redis));

        EmailSubscriber unverified = emailSubscriberRepository.save(EmailSubscriber.create("u@example.com"));
        emailSubscriptionRepository.save(EmailSubscription.of(unverified, redis));
    }

    @Test
    void 인증된_구독자만_수신대상이고_미인증은_제외된다() {
        List<EmailRecipientPair> recipients =
                notificationRepository.findKeywordPathEmailRecipients(LocalDateTime.now().minusDays(2));

        assertThat(recipients).extracting(EmailRecipientPair::emailSubscriberId)
                .containsExactly(verified.getId());
        assertThat(recipients).extracting(EmailRecipientPair::articleId)
                .containsExactly(article.getId());
    }

    @Test
    void 이미_알림보낸_글은_다시_수신대상이_되지_않는다() {
        notificationRepository.save(Notification.ofEmail(verified, article, "Redis"));

        List<EmailRecipientPair> recipients =
                notificationRepository.findKeywordPathEmailRecipients(LocalDateTime.now().minusDays(2));

        assertThat(recipients).isEmpty();
    }
}
