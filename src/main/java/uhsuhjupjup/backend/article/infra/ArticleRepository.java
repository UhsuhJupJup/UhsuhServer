package uhsuhjupjup.backend.article.infra;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.article.domain.Article;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Query("select a from Article a join fetch a.blog where a.id = :id")
    Optional<Article> findWithBlogById(Long id);

    @Query(value = """
            select a from Article a join fetch a.blog
            where (:blogId is null or a.blog.id = :blogId)
            and (:q is null or lower(a.title) like lower(concat('%', :q, '%')))
            and (:allKeywords = true or exists (
                select 1 from ArticleKeyword ak where ak.article = a and ak.keyword.id in :keywordIds))
            and (:allTopics = true or exists (
                select 1 from ArticleKeyword ak2 join TopicKeyword tk on tk.keyword = ak2.keyword
                where ak2.article = a and tk.topic.id in :topicIds))
            order by a.publishedAt desc, a.id desc
            """,
            countQuery = """
            select count(a) from Article a
            where (:blogId is null or a.blog.id = :blogId)
            and (:q is null or lower(a.title) like lower(concat('%', :q, '%')))
            and (:allKeywords = true or exists (
                select 1 from ArticleKeyword ak where ak.article = a and ak.keyword.id in :keywordIds))
            and (:allTopics = true or exists (
                select 1 from ArticleKeyword ak2 join TopicKeyword tk on tk.keyword = ak2.keyword
                where ak2.article = a and tk.topic.id in :topicIds))
            """)
    Page<Article> search(Long blogId, boolean allKeywords, Collection<Long> keywordIds,
                         boolean allTopics, Collection<Long> topicIds, String q, Pageable pageable);

    @Query("select a.url from Article a where a.url in :urls")
    List<String> findExistingUrls(Collection<String> urls);

    @Query("select a from Article a join fetch a.blog where a.classifiedAt is null and a.collectedAt >= :threshold")
    List<Article> findPendingClassificationWithBlog(LocalDateTime threshold);

    @Query("select a from Article a join fetch a.blog where a.id in :ids")
    List<Article> findWithBlogByIdIn(Collection<Long> ids);
}
