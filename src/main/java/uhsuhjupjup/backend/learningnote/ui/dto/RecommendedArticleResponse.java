package uhsuhjupjup.backend.learningnote.ui.dto;

import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.learningnote.application.dto.RecommendedArticleResult;

import java.time.LocalDateTime;
import java.util.List;

public record RecommendedArticleResponse(
        Long articleId,
        String title,
        String url,
        String blogName,
        LocalDateTime publishedAt,
        List<String> matchedKeywords
) {

    public static RecommendedArticleResponse from(RecommendedArticleResult result) {
        Article article = result.article();
        return new RecommendedArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getUrl(),
                article.getBlog().getName(),
                article.getPublishedAt(),
                result.matchedKeywords()
        );
    }
}
