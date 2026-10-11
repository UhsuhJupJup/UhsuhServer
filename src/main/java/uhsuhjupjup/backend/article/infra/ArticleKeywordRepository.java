package uhsuhjupjup.backend.article.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.article.application.dto.KeywordArticleCount;
import uhsuhjupjup.backend.article.application.dto.KeywordEdge;
import uhsuhjupjup.backend.article.application.dto.KeywordFrequency;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighbor;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborLink;
import uhsuhjupjup.backend.article.application.dto.KeywordRelation;
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

    @Query("""
            select new uhsuhjupjup.backend.article.application.dto.KeywordEdge(ak1.keyword.id, ak2.keyword.id, count(distinct ak1.article.id))
            from ArticleKeyword ak1, ArticleKeyword ak2
            where ak1.article.id = ak2.article.id
              and ak1.keyword.id < ak2.keyword.id
            group by ak1.keyword.id, ak2.keyword.id
            order by count(distinct ak1.article.id) desc
            """)
    List<KeywordEdge> findTopCooccurrenceEdges(Pageable pageable);

    @Query("""
            select new uhsuhjupjup.backend.article.application.dto.KeywordFrequency(ak.keyword.id, count(distinct ak.article.id))
            from ArticleKeyword ak
            group by ak.keyword.id
            """)
    List<KeywordFrequency> findKeywordFrequencies();

    @Query("""
            select new uhsuhjupjup.backend.article.application.dto.KeywordFrequency(ak.keyword.id, count(distinct ak.article.id))
            from ArticleKeyword ak
            where ak.keyword.id in :keywordIds
            group by ak.keyword.id
            """)
    List<KeywordFrequency> findKeywordFrequenciesByIds(Collection<Long> keywordIds);

    @Query("select count(ak) from ArticleKeyword ak where ak.keyword.id = :keywordId")
    long countByKeywordId(Long keywordId);

    @Query("""
            select new uhsuhjupjup.backend.article.application.dto.KeywordRelation(ak2.keyword.id, ak2.keyword.name, count(ak2.id))
            from ArticleKeyword ak1, ArticleKeyword ak2
            where ak1.keyword.id = :keywordId
              and ak2.article.id = ak1.article.id
              and ak2.keyword.id <> :keywordId
            group by ak2.keyword.id, ak2.keyword.name
            order by count(ak2.id) desc, ak2.keyword.name asc
            """)
    List<KeywordRelation> findRelatedKeywords(Long keywordId, Pageable pageable);

    @Query("""
            select new uhsuhjupjup.backend.article.application.dto.KeywordNeighborLink(ak2.keyword.id, ak3.keyword.id, count(ak1.id))
            from ArticleKeyword ak1, ArticleKeyword ak2, ArticleKeyword ak3
            where ak1.keyword.id = :keywordId
              and ak2.article.id = ak1.article.id
              and ak3.article.id = ak1.article.id
              and ak2.keyword.id in :neighborIds
              and ak3.keyword.id in :neighborIds
              and ak2.keyword.id < ak3.keyword.id
            group by ak2.keyword.id, ak3.keyword.id
            order by count(ak1.id) desc, ak2.keyword.id asc, ak3.keyword.id asc
            """)
    List<KeywordNeighborLink> findNeighborLinks(Long keywordId, Collection<Long> neighborIds);

    @Query("""
            select new uhsuhjupjup.backend.article.application.dto.KeywordArticleCount(k.id, k.name, counted.articleCount)
            from (
                select ak.keyword.id as keywordId, count(ak.id) as articleCount
                from ArticleKeyword ak
                group by ak.keyword.id
            ) counted
            join Keyword k on k.id = counted.keywordId
            order by counted.articleCount desc, k.name asc
            """)
    List<KeywordArticleCount> findTopKeywords(Pageable pageable);
}
