package uhsuhjupjup.backend.article;

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
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.domain.TopicKeyword;
import uhsuhjupjup.backend.topic.infra.TopicKeywordRepository;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ArticleIntegrationTest extends MySqlTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleKeywordRepository articleKeywordRepository;
    @Autowired
    private BlogRepository blogRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private TopicKeywordRepository topicKeywordRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Article mysqlArticle;
    private Topic database;

    @BeforeEach
    void setUp() {
        Blog woowahan = blogRepository.save(Blog.create("우아한형제들", "techblog.woowahan.com", "https://techblog.woowahan.com/feed.xml"));
        Blog toss = blogRepository.save(Blog.create("토스", "toss.tech", "https://toss.tech/rss.xml"));

        Keyword mysql = keywordRepository.save(Keyword.create("MySQL"));
        Keyword redis = keywordRepository.save(Keyword.create("Redis"));
        Keyword kafka = keywordRepository.save(Keyword.create("Kafka"));

        database = topicRepository.save(Topic.create("Database"));
        topicKeywordRepository.save(TopicKeyword.of(database, mysql));
        topicKeywordRepository.save(TopicKeyword.of(database, redis));

        mysqlArticle = articleRepository.save(Article.create(woowahan, "MySQL 데드락 디버깅 회고", "https://techblog.woowahan.com/12345/", LocalDateTime.of(2026, 6, 15, 11, 0)));
        Article aKafka = articleRepository.save(Article.create(toss, "Kafka 파티션 재조정", "https://toss.tech/kafka1", LocalDateTime.of(2026, 6, 20, 9, 0)));
        Article aRedis = articleRepository.save(Article.create(woowahan, "Redis 캐시 전략", "https://techblog.woowahan.com/redis1", LocalDateTime.of(2026, 6, 18, 10, 0)));
        articleKeywordRepository.save(ArticleKeyword.of(mysqlArticle, mysql, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(aKafka, kafka, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(aRedis, redis, "title"));
    }

    @Test
    void list_returnsAllOrderedByPublishedDesc() throws Exception {
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Kafka 파티션 재조정"))
                .andExpect(jsonPath("$[2].title").value("MySQL 데드락 디버깅 회고"));
    }

    @Test
    void list_byTopicId_returnsTopicArticles() throws Exception {
        mockMvc.perform(get("/api/articles").param("topicId", database.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Redis 캐시 전략"))
                .andExpect(jsonPath("$[1].title").value("MySQL 데드락 디버깅 회고"));
    }

    @Test
    void detail_returnsArticleWithBlogAndKeywords() throws Exception {
        mockMvc.perform(get("/api/articles/" + mysqlArticle.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MySQL 데드락 디버깅 회고"))
                .andExpect(jsonPath("$.blog.domain").value("techblog.woowahan.com"))
                .andExpect(jsonPath("$.keywords[0].name").value("MySQL"))
                .andExpect(jsonPath("$.keywords[0].matchedVia").value("title"));
    }

    @Test
    void detail_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/articles/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
    }
}
