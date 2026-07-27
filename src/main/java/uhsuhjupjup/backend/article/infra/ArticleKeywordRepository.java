package uhsuhjupjup.backend.article.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.article.application.dto.KeywordEdge;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighbor;
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

    @Query("""
            select new uhsuhjupjup.backend.article.application.dto.KeywordNeighbor(ak2.keyword.id, count(distinct ak1.article.id))
            from ArticleKeyword ak1, ArticleKeyword ak2
            where ak1.keyword.id in :keywordIds
              and ak2.article.id = ak1.article.id
              and ak2.keyword.id not in :keywordIds
            group by ak2.keyword.id
            order by count(distinct ak1.article.id) desc
            """)
    List<KeywordNeighbor> findNeighbors(Collection<Long> keywordIds, Pageable pageable);

    @Query("""
            select new uhsuhjupjup.backend.article.application.dto.KeywordEdge(ak1.keyword.id, ak2.keyword.id, count(distinct ak1.article.id))
            from ArticleKeyword ak1, ArticleKeyword ak2
            where ak1.keyword.id in :keywordIds
              and ak2.keyword.id in :keywordIds
              and ak1.article.id = ak2.article.id
              and ak1.keyword.id < ak2.keyword.id
            group by ak1.keyword.id, ak2.keyword.id
            """)
    List<KeywordEdge> findCooccurrenceEdges(Collection<Long> keywordIds);
}
