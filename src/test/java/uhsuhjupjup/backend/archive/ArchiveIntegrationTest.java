package uhsuhjupjup.backend.archive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.notification.domain.Notification;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ArchiveIntegrationTest extends MySqlTestSupport {

    private static final String BEARER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private BlogRepository blogRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleKeywordRepository articleKeywordRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Member member;
    private Article mysqlArticle;

    @BeforeEach
    void setUp() {
        member = Member.create("google", "uid-arch", "arch@example.com");
        member.agreeConsent(LocalDateTime.of(2026, 6, 25, 12, 0));
        memberRepository.save(member);

        Blog woowahan = blogRepository.save(
                Blog.create("우아한형제들", "techblog.woowahan.com", "https://techblog.woowahan.com/feed.xml"));
        Keyword mysql = keywordRepository.save(Keyword.create("MySQL"));
        mysqlArticle = articleRepository.save(
                Article.create(woowahan, "MySQL 데드락 디버깅 회고", "https://techblog.woowahan.com/12345/",
                        LocalDateTime.of(2026, 6, 15, 11, 0)));
        articleKeywordRepository.save(ArticleKeyword.of(mysqlArticle, mysql, "title"));

        given(firebaseTokenVerifier.verify(anyString()))
                .willReturn(new AuthUser("google", "uid-arch", "arch@example.com"));
    }

    @Test
    void sentArticles_returnsMembersNotifications() throws Exception {
        notificationRepository.save(Notification.of(member, mysqlArticle, "MySQL"));

        mockMvc.perform(get("/api/me/notifications").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].article.title").value("MySQL 데드락 디버깅 회고"))
                .andExpect(jsonPath("$.content[0].article.blog.name").value("우아한형제들"))
                .andExpect(jsonPath("$.content[0].article.keywords[0]").value("MySQL"))
                .andExpect(jsonPath("$.content[0].sentAt").exists());
    }

    @Test
    void sentArticles_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/me/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void bookmark_addThenListReturnsIt() throws Exception {
        mockMvc.perform(post("/api/me/bookmarks/" + mysqlArticle.getId()).header("Authorization", BEARER))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/bookmarks").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].article.id").value(mysqlArticle.getId()))
                .andExpect(jsonPath("$.content[0].article.keywords[0]").value("MySQL"))
                .andExpect(jsonPath("$.content[0].bookmarkedAt").exists());
    }

    @Test
    void bookmark_addIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/me/bookmarks/" + mysqlArticle.getId()).header("Authorization", BEARER))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/me/bookmarks/" + mysqlArticle.getId()).header("Authorization", BEARER))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/bookmarks").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void bookmark_removeDeletesIt() throws Exception {
        mockMvc.perform(post("/api/me/bookmarks/" + mysqlArticle.getId()).header("Authorization", BEARER))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/me/bookmarks/" + mysqlArticle.getId()).header("Authorization", BEARER))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/bookmarks").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void bookmark_addNonexistentArticle_returns404() throws Exception {
        mockMvc.perform(post("/api/me/bookmarks/999999").header("Authorization", BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }

    @Test
    void bookmark_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/me/bookmarks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
