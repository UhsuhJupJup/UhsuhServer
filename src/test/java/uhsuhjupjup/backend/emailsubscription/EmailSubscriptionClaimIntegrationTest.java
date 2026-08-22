package uhsuhjupjup.backend.emailsubscription;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscription;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.notification.domain.Notification;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationRepository;
import uhsuhjupjup.backend.subscription.infra.KeywordSubscriptionRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * claim(5단계): 로그인으로 회원이 새로 만들어질 때 같은 이메일의 비회원 구독자를 흡수하는지
 * 실제 MySQL에서 검증한다(알림 re-key, 구독 이동, 구독자 삭제).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmailSubscriptionClaimIntegrationTest extends MySqlTestSupport {

    @Autowired
    private MemberService memberService;
    @Autowired
    private EmailSubscriberRepository emailSubscriberRepository;
    @Autowired
    private EmailSubscriptionRepository emailSubscriptionRepository;
    @Autowired
    private KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private BlogRepository blogRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private EntityManager em;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @Test
    void 로그인하면_같은_이메일_비회원_구독자를_흡수한다() {
        Blog blog = blogRepository.save(
                Blog.create("우아한형제들", "techblog.woowahan.com", "https://techblog.woowahan.com/feed.xml"));
        Keyword redis = keywordRepository.save(Keyword.create("Redis"));
        Article article = articleRepository.save(
                Article.create(blog, "Redis 분산락", "https://ex/1", LocalDateTime.now().minusHours(1)));

        EmailSubscriber subscriber = EmailSubscriber.create("claim@example.com");
        subscriber.verify(LocalDateTime.now());
        subscriber = emailSubscriberRepository.save(subscriber);
        emailSubscriptionRepository.save(EmailSubscription.of(subscriber, redis));
        notificationRepository.save(Notification.ofEmail(subscriber, article, "Redis"));
        Long oldSubscriberId = subscriber.getId();
        // 셋업 엔티티를 컨텍스트에서 분리 — 로그인마다 새 트랜잭션인 실제 흐름을 모사
        em.flush();
        em.clear();

        Member member = memberService.register(new AuthUser("google", "uid-claim", "claim@example.com"));
        em.flush();
        em.clear();

        assertThat(member.getId()).isNotNull();
        // 비회원 구독자·구독은 삭제됨
        assertThat(emailSubscriberRepository.findByEmail("claim@example.com")).isEmpty();
        assertThat(emailSubscriptionRepository.findByEmailSubscriberId(oldSubscriberId)).isEmpty();
        // 구독은 회원 구독으로 이동
        assertThat(keywordSubscriptionRepository.findSubscribedKeywords(member.getId()))
                .extracting(Keyword::getName).containsExactly("Redis");
        // 알림은 회원으로 re-key(member_id로 조회됨, 삭제되지 않음)
        assertThat(notificationRepository.findRecentWithArticleByMemberId(member.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
    }

    @Test
    void 비회원_구독자가_없으면_일반_회원가입만_한다() {
        Member member = memberService.register(new AuthUser("google", "uid-plain", "plain@example.com"));
        em.flush();
        em.clear();

        assertThat(member.getId()).isNotNull();
        assertThat(keywordSubscriptionRepository.findSubscribedKeywords(member.getId())).isEmpty();
    }
}
