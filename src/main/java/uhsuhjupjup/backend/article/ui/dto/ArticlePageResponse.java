package uhsuhjupjup.backend.article.ui.dto;

import uhsuhjupjup.backend.article.application.dto.ArticlePageResult;

import java.util.List;

public record ArticlePageResponse(
        List<ArticleResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

    public static ArticlePageResponse from(ArticlePageResult result) {
        return new ArticlePageResponse(
                result.content().stream().map(ArticleResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext(),
                result.hasPrevious());
    }
}
