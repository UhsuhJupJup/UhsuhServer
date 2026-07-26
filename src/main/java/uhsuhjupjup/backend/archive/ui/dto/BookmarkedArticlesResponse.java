package uhsuhjupjup.backend.archive.ui.dto;

import uhsuhjupjup.backend.archive.application.dto.BookmarkedArticlesResult;
import uhsuhjupjup.backend.article.ui.dto.ArticleResponse;

import java.time.LocalDateTime;
import java.util.List;

public record BookmarkedArticlesResponse(List<BookmarkedArticle> content) {

    public record BookmarkedArticle(ArticleResponse article, LocalDateTime bookmarkedAt) {
    }

    public static BookmarkedArticlesResponse from(BookmarkedArticlesResult result) {
        List<BookmarkedArticle> content = result.content().stream()
                .map(item -> new BookmarkedArticle(ArticleResponse.from(item.article()), item.bookmarkedAt()))
                .toList();
        return new BookmarkedArticlesResponse(content);
    }
}
