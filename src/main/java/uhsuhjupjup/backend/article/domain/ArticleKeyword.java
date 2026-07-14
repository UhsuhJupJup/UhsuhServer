package uhsuhjupjup.backend.article.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.keyword.domain.Keyword;

@Entity
@Table(name = "article_keyword")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    @Column(name = "matched_via", length = 20)
    private String matchedVia;

    private ArticleKeyword(Article article, Keyword keyword, String matchedVia) {
        this.article = article;
        this.keyword = keyword;
        this.matchedVia = matchedVia;
    }

    public static ArticleKeyword of(Article article, Keyword keyword, String matchedVia) {
        return new ArticleKeyword(article, keyword, matchedVia);
    }
}
