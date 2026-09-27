package uhsuhjupjup.backend.techblog.archive.ui.dto;

import uhsuhjupjup.backend.techblog.archive.application.dto.SentArticlesResult;
import uhsuhjupjup.backend.techblog.article.ui.dto.ArticleResponse;

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
