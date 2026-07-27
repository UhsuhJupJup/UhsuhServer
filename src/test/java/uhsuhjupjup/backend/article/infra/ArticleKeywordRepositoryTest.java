package uhsuhjupjup.backend.article.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import uhsuhjupjup.backend.article.application.dto.KeywordEdge;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighbor;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.config.JpaAuditingConfig;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class ArticleKeywordRepositoryTest extends MySqlTestSupport {

    @Autowired
    private ArticleKeywordRepository articleKeywordRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private BlogRepository blogRepository;
    @Autowired
    private KeywordRepository keywordRepository;

    private Keyword mysql;
    private Keyword transaction;
    private Keyword lock;
    private Keyword redis;

    @BeforeEach
    void setUp() {
        Blog blog = blogRepository.save(
                Blog.create("우아한형제들", "techblog.woowahan.com", "https://techblog.woowahan.com/feed.xml"));
        mysql = keywordRepository.save(Keyword.create("MySQL"));
        transaction = keywordRepository.save(Keyword.create("트랜잭션"));
        lock = keywordRepository.save(Keyword.create("락"));
        redis = keywordRepository.save(Keyword.create("Redis"));

        Article a1 = articleRepository.save(
                Article.create(blog, "MySQL 데드락", "https://ex/1", LocalDateTime.of(2026, 6, 1, 0, 0)));
        Article a2 = articleRepository.save(
                Article.create(blog, "MySQL 락", "https://ex/2", LocalDateTime.of(2026, 6, 2, 0, 0)));
        Article a3 = articleRepository.save(
                Article.create(blog, "Redis 분산락", "https://ex/3", LocalDateTime.of(2026, 6, 3, 0, 0)));

        articleKeywordRepository.save(ArticleKeyword.of(a1, mysql, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(a1, transaction, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(a1, lock, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(a2, mysql, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(a2, lock, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(a3, redis, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(a3, lock, "title"));
    }

    @Test
    void 이웃은_동시출현_많은순으로_반환하고_자기키워드는_제외한다() {
        List<KeywordNeighbor> neighbors = articleKeywordRepository.findNeighbors(
                List.of(mysql.getId()), PageRequest.of(0, 10));

        assertThat(neighbors).extracting(KeywordNeighbor::keywordId)
                .containsExactly(lock.getId(), transaction.getId());
        assertThat(neighbors).extracting(KeywordNeighbor::cooccurrence)
                .containsExactly(2L, 1L);
    }

    @Test
    void 여러_키워드_입력시_입력_키워드는_모두_이웃에서_제외된다() {
        List<KeywordNeighbor> neighbors = articleKeywordRepository.findNeighbors(
                List.of(mysql.getId(), lock.getId()), PageRequest.of(0, 10));

        assertThat(neighbors).extracting(KeywordNeighbor::keywordId)
                .containsExactlyInAnyOrder(transaction.getId(), redis.getId());
    }

    @Test
    void 키워드_집합내_동시출현_간선을_반환한다() {
        List<KeywordEdge> edges = articleKeywordRepository.findCooccurrenceEdges(
                List.of(mysql.getId(), transaction.getId(), lock.getId()));

        assertThat(edges).hasSize(3);
        assertThat(edges)
                .filteredOn(edge -> edge.keywordAId().equals(mysql.getId())
                        && edge.keywordBId().equals(lock.getId()))
                .extracting(KeywordEdge::cooccurrence)
                .containsExactly(2L);
    }
}
