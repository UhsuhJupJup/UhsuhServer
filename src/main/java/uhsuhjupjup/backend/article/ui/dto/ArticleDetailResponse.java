package uhsuhjupjup.backend.article.ui.dto;

import uhsuhjupjup.backend.article.application.dto.ArticleDetailResult;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.blog.ui.dto.BlogResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ArticleDetailResponse(
        Long id,
        String title,
        String url,
        LocalDateTime publishedAt,
        BlogResponse blog,
        List<MatchedKeyword> keywords
) {

    public record MatchedKeyword(Long id, String name, String matchedVia) {
    }

    public static ArticleDetailResponse from(ArticleDetailResult result) {
        Article article = result.article();
        List<MatchedKeyword> keywords = result.articleKeywords().stream()
                .map(ak -> new MatchedKeyword(ak.getKeyword().getId(), ak.getKeyword().getName(), ak.getMatchedVia()))
                .toList();
        return new ArticleDetailResponse(
                article.getId(),
                article.getTitle(),
                article.getUrl(),
                article.getPublishedAt(),
                BlogResponse.from(article.getBlog()),
                keywords
        );
    }
}
