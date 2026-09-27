package uhsuhjupjup.backend.techblog.article.application.dto;

import uhsuhjupjup.backend.techblog.article.domain.Article;
import uhsuhjupjup.backend.techblog.article.domain.ArticleKeyword;

import java.util.List;

public record ArticleDetailResult(Article article, List<ArticleKeyword> articleKeywords) {
}
