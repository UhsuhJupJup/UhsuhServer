package uhsuhjupjup.backend.article.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ArticleKeywordRepository extends JpaRepository<ArticleKeyword, Long> {

    @Query("select ak from ArticleKeyword ak join fetch ak.keyword where ak.article.id = :articleId order by ak.keyword.name")
    List<ArticleKeyword> findWithKeywordByArticleId(Long articleId);

    @Query("""
            select ak.article.id from ArticleKeyword ak
            where ak.keyword.id in :keywordIds
            group by ak.article.id
            order by count(distinct ak.keyword.id) desc, max(ak.article.collectedAt) desc
            """)
    List<Long> findTopArticleIdsByKeywordIds(Collection<Long> keywordIds, Pageable pageable);

    @Query("select ak from ArticleKeyword ak join fetch ak.keyword where ak.article.id in :articleIds order by ak.keyword.name")
    List<ArticleKeyword> findWithKeywordByArticleIdIn(Collection<Long> articleIds);

    @Query("select ak.keyword.id from ArticleKeyword ak where ak.article.id = :articleId")
    List<Long> findKeywordIdsByArticleId(Long articleId);

    @Query("""
            select ak.article.id FROM ArticleKeyword ak 
            where ak.keyword.id = :keywordId 
            and ak.article.collectedAt >= :cutOff
            order by ak.article.collectedAt desc
            """)
    List<Long> findCandidateArticleIds(Long keywordId, LocalDateTime cutOff, Pageable pageable);
}
