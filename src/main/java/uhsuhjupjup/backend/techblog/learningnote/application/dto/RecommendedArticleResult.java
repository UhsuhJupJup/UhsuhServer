package uhsuhjupjup.backend.techblog.learningnote.application.dto;

import uhsuhjupjup.backend.techblog.article.domain.Article;

import java.util.List;

public record RecommendedArticleResult(Article article, List<String> matchedKeywords) {
}
