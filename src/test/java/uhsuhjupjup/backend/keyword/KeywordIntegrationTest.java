package uhsuhjupjup.backend.keyword;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
import uhsuhjupjup.backend.keyword.domain.KeywordAlias;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.domain.TopicKeyword;
import uhsuhjupjup.backend.topic.infra.TopicKeywordRepository;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(SharedMySqlTestConfiguration.class)
class KeywordIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private KeywordAliasRepository keywordAliasRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicKeywordRepository topicKeywordRepository;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleKeywordRepository articleKeywordRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Keyword mysql;
    private Topic database;

    @BeforeEach
    void setUp() {
        topicKeywordRepository.deleteAll();
        keywordAliasRepository.deleteAll();
        keywordRepository.deleteAll();
        topicRepository.deleteAll();

        keywordRepository.saveAll(List.of(Keyword.create("Redis"), Keyword.create("Kafka")));
        mysql = keywordRepository.save(Keyword.create("MySQL"));
        keywordAliasRepository.save(KeywordAlias.create(mysql.getId(), "마이에스큐엘"));
        database = topicRepository.save(Topic.create("Database"));
        topicKeywordRepository.save(TopicKeyword.of(database, mysql));
    }

    @Test
    void list_returnsAllOrderedByName() throws Exception {
        mockMvc.perform(get("/api/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Kafka"))
                .andExpect(jsonPath("$[1].name").value("MySQL"))
                .andExpect(jsonPath("$[2].name").value("Redis"));
    }

    @Test
    void list_withQuery_searchesByAlias() throws Exception {
        mockMvc.perform(get("/api/keywords").param("q", "마이"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("MySQL"));
    }

    @Test
    void list_withTopicId_returnsTopicKeywords() throws Exception {
        mockMvc.perform(get("/api/keywords").param("topicId", database.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("MySQL"));
    }

    @Test
    void detail_returnsKeywordWithTopics() throws Exception {
        mockMvc.perform(get("/api/keywords/" + mysql.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MySQL"))
                .andExpect(jsonPath("$.topics.length()").value(1))
                .andExpect(jsonPath("$.topics[0].name").value("Database"));
    }

    @Test
    void detail_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/keywords/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEYWORD_NOT_FOUND"));
    }

    @Test
    void detail_returnsArticleCountAndRelatedKeywords() throws Exception {
        Keyword jpa = keywordRepository.save(Keyword.create("JPA"));
        Keyword hibernate = keywordRepository.save(Keyword.create("Hibernate"));
        Blog blog = blogRepository.save(
                Blog.create("키워드 통합 테스트", "keyword-it.example.com", "https://keyword-it.example.com/rss"));
        Article first = saveArticle(blog, 1);
        Article second = saveArticle(blog, 2);
        Article third = saveArticle(blog, 3);
        attach(first, mysql, jpa, hibernate);
        attach(second, mysql, jpa);
        attach(third, mysql);
        attach(saveArticle(blog, 4), jpa);

        mockMvc.perform(get("/api/keywords/" + mysql.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MySQL"))
                .andExpect(jsonPath("$.topics[0].name").value("Database"))
                .andExpect(jsonPath("$.articleCount").value(3))
                .andExpect(jsonPath("$.relatedKeywords.length()").value(2))
                .andExpect(jsonPath("$.relatedKeywords[0].id").value(jpa.getId()))
                .andExpect(jsonPath("$.relatedKeywords[0].name").value("JPA"))
                .andExpect(jsonPath("$.relatedKeywords[0].sharedArticleCount").value(2))
                .andExpect(jsonPath("$.relatedKeywords[1].name").value("Hibernate"))
                .andExpect(jsonPath("$.relatedKeywords[1].sharedArticleCount").value(1));
        mockMvc.perform(get("/api/articles").param("keywordIds", mysql.getId().toString()).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void detail_withoutArticles_returnsZeroAndEmptyRelatedKeywords() throws Exception {
        mockMvc.perform(get("/api/keywords/" + mysql.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleCount").value(0))
                .andExpect(jsonPath("$.relatedKeywords").isArray())
                .andExpect(jsonPath("$.relatedKeywords").isEmpty());
    }

    private Article saveArticle(Blog blog, int number) {
        return articleRepository.save(Article.create(
                blog,
                "키워드 통합 테스트 글 " + number,
                "https://keyword-it.example.com/" + number,
                LocalDateTime.of(2026, 6, number, 0, 0)));
    }

    private void attach(Article article, Keyword... keywords) {
        for (Keyword keyword : keywords) {
            articleKeywordRepository.save(ArticleKeyword.of(article, keyword, "title"));
        }
    }
}
