package uhsuhjupjup.backend.learningnote.application.dto;

import uhsuhjupjup.backend.article.domain.Article;

import java.util.List;

public record RecommendedArticleResult(Article article, List<String> matchedKeywords) {
}
