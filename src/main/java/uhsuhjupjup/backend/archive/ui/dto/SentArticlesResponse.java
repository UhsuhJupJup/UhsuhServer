package uhsuhjupjup.backend.archive.ui.dto;

import uhsuhjupjup.backend.archive.application.dto.SentArticlesResult;
import uhsuhjupjup.backend.article.ui.dto.ArticleResponse;

import java.time.LocalDateTime;
import java.util.List;

public record SentArticlesResponse(List<SentArticle> content) {

    public record SentArticle(ArticleResponse article, LocalDateTime sentAt) {
    }

    public static SentArticlesResponse from(SentArticlesResult result) {
        List<SentArticle> content = result.content().stream()
                .map(item -> new SentArticle(ArticleResponse.from(item.article()), item.sentAt()))
                .toList();
        return new SentArticlesResponse(content);
    }
}
