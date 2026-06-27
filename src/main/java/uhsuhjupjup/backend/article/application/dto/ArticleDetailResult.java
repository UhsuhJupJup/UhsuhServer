package uhsuhjupjup.backend.article.application.dto;

import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;

import java.util.List;

public record ArticleDetailResult(Article article, List<ArticleKeyword> articleKeywords) {
}
