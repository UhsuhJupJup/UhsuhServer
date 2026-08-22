package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.notification.application.DigestRenderer;
import uhsuhjupjup.backend.pipeline.notification.application.NotificationSaver;
import uhsuhjupjup.backend.pipeline.notification.application.NotificationService;
import uhsuhjupjup.backend.pipeline.notification.application.dto.DigestArticleView;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailRecipientPair;
import uhsuhjupjup.backend.pipeline.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.pipeline.notification.application.dto.RecipientPair;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationRepository;
import uhsuhjupjup.backend.support.ArticleFixture;
import uhsuhjupjup.backend.support.BlogFixture;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ArticleRepository articleRepository;
    @Mock
    private ArticleKeywordRepository articleKeywordRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;
    @Mock
    private DigestRenderer digestRenderer;
    @Mock
    private NotificationSaver notificationSaver;

    @Captor
    private ArgumentCaptor<List<DigestArticleView>> viewsCaptor;

    private FakeEmailSender emailSender;
    private NotificationService service;

    private final Blog blog = BlogFixture.blog(1L, "우아한형제들", "techblog.woowahan.com");
    private final Member member1 = member(1L, "a@test.com");
    private final Member member2 = member(2L, "b@test.com");
    private final Article article10 = ArticleFixture.article(10L, blog, "글10", "https://t.com/10",
            LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 10, 0));
    private final Article article20 = ArticleFixture.article(20L, blog, "글20", "https://t.com/20",
            LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 10, 0));

    @BeforeEach
    void setUp() {
        emailSender = new FakeEmailSender();
        service = new NotificationService(notificationRepository, articleRepository, articleKeywordRepository,
                memberRepository, emailSubscriberRepository, digestRenderer, emailSender, notificationSaver);
        ReflectionTestUtils.setField(service, "maxPerMember", 5);
    }

    @Test
    void notifyRecent_batchesMultipleArticlesIntoOneEmail() {
        given(notificationRepository.findKeywordPathRecipients(any()))
                .willReturn(List.of(new RecipientPair(1L, 10L), new RecipientPair(1L, 20L)));
        given(articleRepository.findWithBlogByIdIn(any())).willReturn(List.of(article10, article20));
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(any())).willReturn(List.of());
        given(memberRepository.findAllById(any())).willReturn(List.of(member1));
        given(digestRenderer.render(any(), any(), any())).willReturn("<html>");
        given(notificationSaver.record(eq(1L), any())).willReturn(2);

        NotificationResult result = service.notifyRecent();

        assertThat(emailSender.sent()).hasSize(1);
        assertThat(emailSender.sent().get(0).to()).isEqualTo("a@test.com");
        assertThat(result.membersNotified()).isEqualTo(1);
        assertThat(result.notificationsRecorded()).isEqualTo(2);
        verify(digestRenderer).render(eq(member1), viewsCaptor.capture(), any());
        assertThat(viewsCaptor.getValue()).hasSize(2);
    }

    @Test
    void notifyRecent_capsToTopFiveByCollectedDesc() {
        List<Article> articles = new ArrayList<>();
        List<RecipientPair> pairs = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            long id = i;
            articles.add(ArticleFixture.article(id, blog, "글" + i, "https://t.com/" + i,
                    LocalDateTime.of(2026, 6, 1, 0, 0),
                    LocalDateTime.of(2026, 6, 1, 0, 0).plusHours(i)));
            pairs.add(new RecipientPair(1L, id));
        }
        given(notificationRepository.findKeywordPathRecipients(any())).willReturn(pairs);
        given(articleRepository.findWithBlogByIdIn(any())).willReturn(articles);
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(any())).willReturn(List.of());
        given(memberRepository.findAllById(any())).willReturn(List.of(member1));
        given(digestRenderer.render(any(), any(), any())).willReturn("<html>");
        given(notificationSaver.record(eq(1L), any())).willReturn(5);

        NotificationResult result = service.notifyRecent();

        verify(digestRenderer).render(eq(member1), viewsCaptor.capture(), any());
        assertThat(viewsCaptor.getValue()).extracting(DigestArticleView::title)
                .containsExactly("글7", "글6", "글5", "글4", "글3");
        assertThat(result.notificationsRecorded()).isEqualTo(5);
    }

    @Test
    void notifyRecent_sendsOneEmailPerMember() {
        given(notificationRepository.findKeywordPathRecipients(any()))
                .willReturn(List.of(new RecipientPair(1L, 10L), new RecipientPair(2L, 10L)));
        given(articleRepository.findWithBlogByIdIn(any())).willReturn(List.of(article10));
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(any())).willReturn(List.of());
        given(memberRepository.findAllById(any())).willReturn(List.of(member1, member2));
        given(digestRenderer.render(any(), any(), any())).willReturn("<html>");
        given(notificationSaver.record(anyLong(), any())).willReturn(1);

        NotificationResult result = service.notifyRecent();

        assertThat(emailSender.sent()).hasSize(2);
        assertThat(result.membersNotified()).isEqualTo(2);
        assertThat(result.notificationsRecorded()).isEqualTo(2);
    }

    @Test
    void notifyRecent_dedupsRepeatedArticleForMember() {
        given(notificationRepository.findKeywordPathRecipients(any()))
                .willReturn(List.of(new RecipientPair(1L, 10L), new RecipientPair(1L, 10L)));
        given(articleRepository.findWithBlogByIdIn(any())).willReturn(List.of(article10));
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(any())).willReturn(List.of());
        given(memberRepository.findAllById(any())).willReturn(List.of(member1));
        given(digestRenderer.render(any(), any(), any())).willReturn("<html>");
        given(notificationSaver.record(eq(1L), any())).willReturn(1);

        service.notifyRecent();

        verify(digestRenderer).render(eq(member1), viewsCaptor.capture(), any());
        assertThat(viewsCaptor.getValue()).hasSize(1);
    }

    @Test
    void notifyRecent_isolatesPerMemberFailure() {
        given(notificationRepository.findKeywordPathRecipients(any()))
                .willReturn(List.of(new RecipientPair(1L, 10L), new RecipientPair(2L, 20L)));
        given(articleRepository.findWithBlogByIdIn(any())).willReturn(List.of(article10, article20));
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(any())).willReturn(List.of());
        given(memberRepository.findAllById(any())).willReturn(List.of(member1, member2));
        given(digestRenderer.render(any(), any(), any())).willReturn("<html>");
        given(notificationSaver.record(eq(2L), any())).willReturn(1);
        emailSender.failFor("a@test.com");

        NotificationResult result = service.notifyRecent();

        assertThat(emailSender.sent()).hasSize(1);
        assertThat(emailSender.sent().get(0).to()).isEqualTo("b@test.com");
        assertThat(result.membersNotified()).isEqualTo(1);
        assertThat(result.failedMembers()).isEqualTo(1);
    }

    @Test
    void notifyRecent_whenNoRecipients_sendsNothing() {
        given(notificationRepository.findKeywordPathRecipients(any())).willReturn(List.of());

        NotificationResult result = service.notifyRecent();

        assertThat(emailSender.sent()).isEmpty();
        assertThat(result.membersNotified()).isZero();
    }

    @Test
    void notifyRecent_sendsDigestToVerifiedEmailSubscribers() {
        given(notificationRepository.findKeywordPathRecipients(any())).willReturn(List.of());
        given(notificationRepository.findKeywordPathEmailRecipients(any()))
                .willReturn(List.of(new EmailRecipientPair(100L, 10L)));
        given(articleRepository.findWithBlogByIdIn(any())).willReturn(List.of(article10));
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(any())).willReturn(List.of());
        given(emailSubscriberRepository.findAllById(any()))
                .willReturn(List.of(emailSubscriber(100L, "sub@test.com")));
        given(digestRenderer.render(any(String.class), any(), any(), any())).willReturn("<html>");
        given(digestRenderer.unsubscribeUrl(any(String.class))).willReturn("https://uhsuh/u");
        given(notificationSaver.recordEmail(eq(100L), any())).willReturn(1);

        NotificationResult result = service.notifyRecent();

        assertThat(emailSender.sent()).hasSize(1);
        assertThat(emailSender.sent().get(0).to()).isEqualTo("sub@test.com");
        assertThat(result.emailSubscribersNotified()).isEqualTo(1);
        assertThat(result.membersNotified()).isZero();
        assertThat(result.notificationsRecorded()).isEqualTo(1);
    }

    private Member member(Long id, String email) {
        Member member = Member.create("google", "uid" + id, email);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private EmailSubscriber emailSubscriber(Long id, String email) {
        EmailSubscriber subscriber = EmailSubscriber.create(email);
        ReflectionTestUtils.setField(subscriber, "id", id);
        return subscriber;
    }
}
