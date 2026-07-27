package uhsuhjupjup.backend.learningnote;

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
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteRecommendationIntegrationTest extends MySqlTestSupport {

    private static final String BEARER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private LearningNoteRepository learningNoteRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private BlogRepository blogRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleKeywordRepository articleKeywordRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockitoBean
    private KeywordClassifier keywordClassifier;

    private Keyword redis;
    private Keyword caching;
    private Article redisCachingArticle;
    private Article redisOnlyArticle;
    private Long noteId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.create("github", "uid-int", "rec@example.com"));
        given(firebaseTokenVerifier.verify(anyString()))
                .willReturn(new AuthUser("github", "uid-int", "rec@example.com"));

        redis = keywordRepository.save(Keyword.create("Redis"));
        caching = keywordRepository.save(Keyword.create("캐싱"));
        Keyword jpa = keywordRepository.save(Keyword.create("JPA"));

        Blog blog = blogRepository.save(Blog.create("우아한형제들", "woowahan.com", "https://woowahan.com/rss"));
        redisCachingArticle = articleRepository.save(
                Article.create(blog, "Redis 캐시 전략", "https://woowahan.com/redis-cache", LocalDateTime.now()));
        redisOnlyArticle = articleRepository.save(
                Article.create(blog, "Redis 입문", "https://woowahan.com/redis-intro", LocalDateTime.now()));
        Article jpaArticle = articleRepository.save(
                Article.create(blog, "JPA N+1", "https://woowahan.com/jpa-nplus1", LocalDateTime.now()));

        articleKeywordRepository.save(ArticleKeyword.of(redisCachingArticle, redis, "ai"));
        articleKeywordRepository.save(ArticleKeyword.of(redisCachingArticle, caching, "ai"));
        articleKeywordRepository.save(ArticleKeyword.of(redisOnlyArticle, redis, "ai"));
        articleKeywordRepository.save(ArticleKeyword.of(jpaArticle, jpa, "ai"));

        noteId = learningNoteRepository.save(
                LearningNote.create(member, "학습 노트", "Redis 캐싱 정리")).getId();
    }

    @Test
    void 노트_키워드와_겹치는_글을_겹침수_순으로_추천한다() throws Exception {
        given(keywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(caching.getId(), "ai")));

        mockMvc.perform(get("/api/notes/" + noteId + "/recommendations").header(AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(2))
                .andExpect(jsonPath("$.keywords", hasItems("Redis", "캐싱")))
                .andExpect(jsonPath("$.articles.length()").value(2))
                .andExpect(jsonPath("$.articles[0].articleId").value(redisCachingArticle.getId()))
                .andExpect(jsonPath("$.articles[0].blogName").value("우아한형제들"))
                .andExpect(jsonPath("$.articles[0].matchedKeywords.length()").value(2))
                .andExpect(jsonPath("$.articles[1].articleId").value(redisOnlyArticle.getId()))
                .andExpect(jsonPath("$.articles[1].matchedKeywords.length()").value(1))
                .andExpect(jsonPath("$.articles[1].matchedKeywords[0]").value("Redis"));
    }

    @Test
    void 카탈로그_키워드가_없으면_빈_목록을_반환한다() throws Exception {
        given(keywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(List.of());

        mockMvc.perform(get("/api/notes/" + noteId + "/recommendations").header(AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(0))
                .andExpect(jsonPath("$.articles.length()").value(0));
    }

    @Test
    void 키워드는_추출됐지만_겹치는_글이_없으면_키워드만_반환한다() throws Exception {
        Keyword kafka = keywordRepository.save(Keyword.create("Kafka"));
        given(keywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(List.of(new KeywordMatch(kafka.getId(), "ai")));

        mockMvc.perform(get("/api/notes/" + noteId + "/recommendations").header(AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(1))
                .andExpect(jsonPath("$.keywords[0]").value("Kafka"))
                .andExpect(jsonPath("$.articles.length()").value(0));
    }

    @Test
    void 인증_없이_추천은_401() throws Exception {
        mockMvc.perform(get("/api/notes/" + noteId + "/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 남의_노트_추천은_404() throws Exception {
        memberRepository.save(Member.create("github", "uid-other", "other@example.com"));
        given(firebaseTokenVerifier.verify(anyString()))
                .willReturn(new AuthUser("github", "uid-other", "other@example.com"));

        mockMvc.perform(get("/api/notes/" + noteId + "/recommendations").header(AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound());
    }

    @Test
    void 노트_그래프는_노트_키워드_추천글_노드와_간선을_반환한다() throws Exception {
        given(keywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(caching.getId(), "ai")));

        mockMvc.perform(get("/api/notes/" + noteId + "/graph").header(AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isNotEmpty())
                .andExpect(jsonPath("$.edges").isNotEmpty())
                .andExpect(jsonPath("$.nodes[?(@.id=='note:" + noteId + "')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.id=='kw:" + redis.getId() + "')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.type=='article')]").exists())
                .andExpect(jsonPath("$.edges[?(@.source=='note:" + noteId + "')]").exists());
    }

    @Test
    void 인증_없이_그래프는_401() throws Exception {
        mockMvc.perform(get("/api/notes/" + noteId + "/graph"))
                .andExpect(status().isUnauthorized());
    }
}
