package uhsuhjupjup.backend.article.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.config.JpaAuditingConfig;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.domain.TopicKeyword;
import uhsuhjupjup.backend.topic.infra.TopicKeywordRepository;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class ArticleRepositoryTest extends MySqlTestSupport {

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

    private static final Pageable PAGE = PageRequest.of(0, 20);

    private Blog toss;
    private Keyword redis;
    private Topic database;

    @BeforeEach
    void setUp() {
        Blog woowahan = blogRepository.save(Blog.create("우아한형제들", "techblog.woowahan.com", "https://techblog.woowahan.com/feed.xml"));
        toss = blogRepository.save(Blog.create("토스", "toss.tech", "https://toss.tech/rss.xml"));

        Keyword mysql = keywordRepository.save(Keyword.create("MySQL"));
        redis = keywordRepository.save(Keyword.create("Redis"));
        Keyword kafka = keywordRepository.save(Keyword.create("Kafka"));

        database = topicRepository.save(Topic.create("Database"));
        topicKeywordRepository.save(TopicKeyword.of(database, mysql));
        topicKeywordRepository.save(TopicKeyword.of(database, redis));

        Article aMysql = articleRepository.save(Article.create(woowahan, "MySQL 데드락 디버깅 회고", "https://techblog.woowahan.com/12345/", LocalDateTime.of(2026, 6, 15, 11, 0)));
        Article aKafka = articleRepository.save(Article.create(toss, "Kafka 파티션 재조정", "https://toss.tech/kafka1", LocalDateTime.of(2026, 6, 20, 9, 0)));
        Article aRedis = articleRepository.save(Article.create(woowahan, "Redis 캐시 전략", "https://techblog.woowahan.com/redis1", LocalDateTime.of(2026, 6, 18, 10, 0)));
        articleKeywordRepository.save(ArticleKeyword.of(aMysql, mysql, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(aKafka, kafka, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(aRedis, redis, "title"));
    }

    @Test
    void search_all_orderedByPublishedDesc() {
        assertThat(articleRepository.search(null, null, null, null, PAGE))
                .extracting(Article::getTitle)
                .containsExactly("Kafka 파티션 재조정", "Redis 캐시 전략", "MySQL 데드락 디버깅 회고");
    }

    @Test
    void search_byBlogId() {
        assertThat(articleRepository.search(toss.getId(), null, null, null, PAGE))
                .extracting(Article::getTitle)
                .containsExactly("Kafka 파티션 재조정");
    }

    @Test
    void search_byKeywordId() {
        assertThat(articleRepository.search(null, redis.getId(), null, null, PAGE))
                .extracting(Article::getTitle)
                .containsExactly("Redis 캐시 전략");
    }

    @Test
    void search_byTopicId() {
        assertThat(articleRepository.search(null, null, database.getId(), null, PAGE))
                .extracting(Article::getTitle)
                .containsExactly("Redis 캐시 전략", "MySQL 데드락 디버깅 회고");
    }

    @Test
    void search_byQuery_matchesTitle() {
        assertThat(articleRepository.search(null, null, null, "kafka", PAGE))
                .extracting(Article::getTitle)
                .containsExactly("Kafka 파티션 재조정");
    }

    @Test
    void search_respectsLimit() {
        assertThat(articleRepository.search(null, null, null, null, PageRequest.of(0, 1)))
                .extracting(Article::getTitle)
                .containsExactly("Kafka 파티션 재조정");
    }
}
