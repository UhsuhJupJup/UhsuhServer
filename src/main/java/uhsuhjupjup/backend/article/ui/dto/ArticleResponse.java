package uhsuhjupjup.backend.article.ui.dto;

import uhsuhjupjup.backend.article.application.dto.ArticleSummaryResult;
import uhsuhjupjup.backend.article.domain.Article;

import java.time.LocalDateTime;
import java.util.List;

public record ArticleResponse(
        Long id,
        String title,
        String url,
        LocalDateTime publishedAt,
        BlogSummary blog,
        List<String> keywords
) {

    public record BlogSummary(Long id, String name) {
    }

    public static ArticleResponse from(ArticleSummaryResult result) {
        Article article = result.article();
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getUrl(),
                article.getPublishedAt(),
                new BlogSummary(article.getBlog().getId(), article.getBlog().getName()),
                result.keywordNames()
        );
    }
}
