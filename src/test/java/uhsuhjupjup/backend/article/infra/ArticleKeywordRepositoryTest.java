package uhsuhjupjup.backend.article.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import uhsuhjupjup.backend.article.application.dto.KeywordEdge;
import uhsuhjupjup.backend.article.application.dto.KeywordFrequency;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighbor;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborLink;
import uhsuhjupjup.backend.article.application.dto.KeywordRelation;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@MySqlDataJpaTest
class ArticleKeywordRepositoryTest {

    @Autowired
    private ArticleKeywordRepository articleKeywordRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private BlogRepository blogRepository;
    @Autowired
    private KeywordRepository keywordRepository;

    private Blog blog;
    private Keyword mysql;
    private Keyword transaction;
    private Keyword lock;
    private Keyword redis;

    @BeforeEach
    void setUp() {
        blog = blogRepository.save(
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

    @Test
    void 전역_동시출현_간선을_많은순으로_반환한다() {
        List<KeywordEdge> edges = articleKeywordRepository.findTopCooccurrenceEdges(PageRequest.of(0, 10));

        assertThat(edges).hasSize(4);
        assertThat(edges.get(0)).extracting(KeywordEdge::keywordAId, KeywordEdge::keywordBId, KeywordEdge::cooccurrence)
                .containsExactly(mysql.getId(), lock.getId(), 2L);
        assertThat(edges).extracting(KeywordEdge::cooccurrence).containsExactly(2L, 1L, 1L, 1L);
    }

    @Test
    void 전역_간선은_top_N으로_제한된다() {
        List<KeywordEdge> edges = articleKeywordRepository.findTopCooccurrenceEdges(PageRequest.of(0, 1));

        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).cooccurrence()).isEqualTo(2L);
    }

    @Test
    void 키워드별_글수를_반환한다() {
        Map<Long, Long> countByKeyword = articleKeywordRepository.findKeywordFrequencies().stream()
                .collect(Collectors.toMap(KeywordFrequency::keywordId, KeywordFrequency::articleCount));

        assertThat(countByKeyword).containsOnly(
                Map.entry(lock.getId(), 3L),
                Map.entry(mysql.getId(), 2L),
                Map.entry(transaction.getId(), 1L),
                Map.entry(redis.getId(), 1L));
    }

    @Test
    void 지정_키워드의_글수만_반환한다() {
        List<KeywordFrequency> frequencies = articleKeywordRepository.findKeywordFrequenciesByIds(
                List.of(lock.getId(), redis.getId()));

        assertThat(frequencies).extracting(KeywordFrequency::keywordId, KeywordFrequency::articleCount)
                .containsExactlyInAnyOrder(tuple(lock.getId(), 3L), tuple(redis.getId(), 1L));
    }

    @Test
    void 키워드에_붙은_글의_수를_센다() {
        Keyword unused = keywordRepository.save(Keyword.create("Kotlin"));

        assertThat(articleKeywordRepository.countByKeywordId(lock.getId())).isEqualTo(3);
        assertThat(articleKeywordRepository.countByKeywordId(mysql.getId())).isEqualTo(2);
        assertThat(articleKeywordRepository.countByKeywordId(unused.getId())).isZero();
    }

    @Test
    void 연관_키워드는_함께_붙은_글이_많은순이고_자기_자신은_없다() {
        List.of(4, 5).forEach(number -> {
            Article article = articleRepository.save(Article.create(
                    blog, "MySQL 트랜잭션 " + number, "https://ex/" + number, LocalDateTime.of(2026, 6, number, 0, 0)));
            articleKeywordRepository.save(ArticleKeyword.of(article, mysql, "title"));
            articleKeywordRepository.save(ArticleKeyword.of(article, transaction, "title"));
        });

        List<KeywordRelation> related = articleKeywordRepository.findRelatedKeywords(
                mysql.getId(), PageRequest.of(0, 8));

        assertThat(related)
                .extracting(KeywordRelation::keywordId, KeywordRelation::name, KeywordRelation::sharedArticleCount)
                .containsExactly(
                        tuple(transaction.getId(), "트랜잭션", 3L),
                        tuple(lock.getId(), "락", 2L));
    }

    @Test
    void 함께_붙은_글_수가_같으면_대소문자를_가리지_않는_이름순이다() {
        Keyword cloud = keywordRepository.save(Keyword.create("클라우드"));
        Keyword bigtable = keywordRepository.save(Keyword.create("Bigtable"));
        Keyword azure = keywordRepository.save(Keyword.create("Azure"));
        Keyword aws = keywordRepository.save(Keyword.create("aws"));
        Article article = articleRepository.save(
                Article.create(blog, "클라우드 비교", "https://ex/cloud", LocalDateTime.of(2026, 6, 4, 0, 0)));
        List.of(cloud, bigtable, azure, aws)
                .forEach(keyword -> articleKeywordRepository.save(ArticleKeyword.of(article, keyword, "title")));

        List<KeywordRelation> related = articleKeywordRepository.findRelatedKeywords(
                cloud.getId(), PageRequest.of(0, 8));

        List<Long> relatedIds = related.stream().map(KeywordRelation::keywordId).toList();
        List<String> namesInListOrder = keywordRepository.findAllByOrderByNameAsc().stream()
                .filter(keyword -> relatedIds.contains(keyword.getId()))
                .map(Keyword::getName)
                .toList();
        assertThat(related).extracting(KeywordRelation::name).containsExactly("aws", "Azure", "Bigtable");
        assertThat(related).extracting(KeywordRelation::name).containsExactlyElementsOf(namesInListOrder);
        assertThat(related).extracting(KeywordRelation::sharedArticleCount).containsOnly(1L);
    }

    @Test
    void 연관_키워드가_요청한_수보다_많으면_순서대로_그만큼만_반환한다() {
        Keyword hub = keywordRepository.save(Keyword.create("허브"));
        Article shared = articleRepository.save(
                Article.create(blog, "키워드가 많은 글", "https://ex/hub", LocalDateTime.of(2026, 6, 5, 0, 0)));
        Article second = articleRepository.save(
                Article.create(blog, "허브와 k9", "https://ex/hub-2", LocalDateTime.of(2026, 6, 6, 0, 0)));
        articleKeywordRepository.save(ArticleKeyword.of(shared, hub, "title"));
        articleKeywordRepository.save(ArticleKeyword.of(second, hub, "title"));
        List<Keyword> numbered = IntStream.iterate(9, number -> number - 1).limit(9)
                .mapToObj(number -> keywordRepository.save(Keyword.create("k" + number)))
                .toList();
        numbered.forEach(keyword -> articleKeywordRepository.save(ArticleKeyword.of(shared, keyword, "title")));
        articleKeywordRepository.save(ArticleKeyword.of(second, numbered.get(0), "title"));

        List<KeywordRelation> related = articleKeywordRepository.findRelatedKeywords(
                hub.getId(), PageRequest.of(0, 8));

        assertThat(related).extracting(KeywordRelation::name, KeywordRelation::sharedArticleCount)
                .containsExactly(
                        tuple("k9", 2L),
                        tuple("k1", 1L), tuple("k2", 1L), tuple("k3", 1L), tuple("k4", 1L),
                        tuple("k5", 1L), tuple("k6", 1L), tuple("k7", 1L));
    }

    @Test
    void 글이_없는_키워드는_연관_키워드가_없다() {
        Keyword unused = keywordRepository.save(Keyword.create("Kotlin"));

        assertThat(articleKeywordRepository.findRelatedKeywords(unused.getId(), PageRequest.of(0, 8))).isEmpty();
    }

    @Test
    void 이웃끼리의_연결은_중심_키워드의_글_안에서만_센다() {
        Keyword hub = keywordRepository.save(Keyword.create("허브"));
        Keyword first = keywordRepository.save(Keyword.create("첫째"));
        Keyword second = keywordRepository.save(Keyword.create("둘째"));
        attach(saveArticle("g1"), hub, first, second);
        attach(saveArticle("g2"), hub, first, second);
        attach(saveArticle("g3"), hub, first);
        attach(saveArticle("g4"), first, second);
        attach(saveArticle("g5"), first, second);

        List<KeywordNeighborLink> links = articleKeywordRepository.findNeighborLinks(
                hub.getId(), List.of(first.getId(), second.getId()));

        assertThat(links)
                .extracting(
                        KeywordNeighborLink::sourceKeywordId,
                        KeywordNeighborLink::targetKeywordId,
                        KeywordNeighborLink::sharedArticleCount)
                .containsExactly(tuple(first.getId(), second.getId(), 2L));
    }

    @Test
    void 중심_키워드가_없는_글에서만_함께_붙은_이웃은_연결되지_않는다() {
        Keyword hub = keywordRepository.save(Keyword.create("허브"));
        Keyword first = keywordRepository.save(Keyword.create("첫째"));
        Keyword second = keywordRepository.save(Keyword.create("둘째"));
        attach(saveArticle("g1"), hub, first);
        attach(saveArticle("g2"), hub, second);
        attach(saveArticle("g3"), first, second);

        List<KeywordNeighborLink> links = articleKeywordRepository.findNeighborLinks(
                hub.getId(), List.of(first.getId(), second.getId()));

        assertThat(links).isEmpty();
    }

    @Test
    void 이웃끼리의_연결은_넘긴_이웃_사이에서만_나온다() {
        Keyword hub = keywordRepository.save(Keyword.create("허브"));
        Keyword first = keywordRepository.save(Keyword.create("첫째"));
        Keyword second = keywordRepository.save(Keyword.create("둘째"));
        Keyword outside = keywordRepository.save(Keyword.create("목록 밖"));
        attach(saveArticle("g1"), hub, first, second, outside);

        List<KeywordNeighborLink> links = articleKeywordRepository.findNeighborLinks(
                hub.getId(), List.of(first.getId(), second.getId()));

        assertThat(links)
                .extracting(KeywordNeighborLink::sourceKeywordId, KeywordNeighborLink::targetKeywordId)
                .containsExactly(tuple(first.getId(), second.getId()));
    }

    @Test
    void 이웃끼리의_연결은_쌍마다_한_번이고_글이_많은순_다음_id순이다() {
        Keyword hub = keywordRepository.save(Keyword.create("허브"));
        Keyword first = keywordRepository.save(Keyword.create("첫째"));
        Keyword second = keywordRepository.save(Keyword.create("둘째"));
        Keyword third = keywordRepository.save(Keyword.create("셋째"));
        Keyword fourth = keywordRepository.save(Keyword.create("넷째"));
        attach(saveArticle("g1"), hub, second, third);
        attach(saveArticle("g2"), hub, first, fourth);
        attach(saveArticle("g3"), hub, third, fourth);
        attach(saveArticle("g4"), hub, third, fourth);

        List<KeywordNeighborLink> links = articleKeywordRepository.findNeighborLinks(
                hub.getId(), List.of(fourth.getId(), third.getId(), second.getId(), first.getId()));

        assertThat(links)
                .extracting(
                        KeywordNeighborLink::sourceKeywordId,
                        KeywordNeighborLink::targetKeywordId,
                        KeywordNeighborLink::sharedArticleCount)
                .containsExactly(
                        tuple(third.getId(), fourth.getId(), 2L),
                        tuple(first.getId(), fourth.getId(), 1L),
                        tuple(second.getId(), third.getId(), 1L));
    }

    private Article saveArticle(String slug) {
        return articleRepository.save(
                Article.create(blog, "그래프 " + slug, "https://ex/" + slug, LocalDateTime.of(2026, 6, 10, 0, 0)));
    }

    private void attach(Article article, Keyword... keywords) {
        for (Keyword keyword : keywords) {
            articleKeywordRepository.save(ArticleKeyword.of(article, keyword, "title"));
        }
    }
}
