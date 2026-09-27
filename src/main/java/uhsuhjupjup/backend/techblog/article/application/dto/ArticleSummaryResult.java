package uhsuhjupjup.backend.techblog.article.application.dto;

import uhsuhjupjup.backend.techblog.article.domain.Article;

import java.util.List;

public record ArticleSummaryResult(Article article, List<String> keywordNames) {
}
