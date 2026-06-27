package uhsuhjupjup.backend.article.application;

import uhsuhjupjup.backend.article.domain.Article;

import java.util.List;

public record ArticleSummaryResult(Article article, List<String> keywordNames) {
}
