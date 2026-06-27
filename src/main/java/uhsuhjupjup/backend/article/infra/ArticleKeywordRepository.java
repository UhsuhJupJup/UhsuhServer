package uhsuhjupjup.backend.article.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;

import java.util.Collection;
import java.util.List;

public interface ArticleKeywordRepository extends JpaRepository<ArticleKeyword, Long> {

    @Query("select ak from ArticleKeyword ak join fetch ak.keyword where ak.article.id = :articleId order by ak.keyword.name")
    List<ArticleKeyword> findWithKeywordByArticleId(Long articleId);

    @Query("select ak from ArticleKeyword ak join fetch ak.keyword where ak.article.id in :articleIds order by ak.keyword.name")
    List<ArticleKeyword> findWithKeywordByArticleIdIn(Collection<Long> articleIds);
}
