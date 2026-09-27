package uhsuhjupjup.backend.techblog.article.application.dto;

import java.util.List;

public record ArticlePageResult(
        List<ArticleSummaryResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
}
