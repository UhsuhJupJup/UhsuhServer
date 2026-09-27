package uhsuhjupjup.backend.techblog.archive.application.dto;

import uhsuhjupjup.backend.techblog.article.application.dto.ArticleSummaryResult;

import java.time.LocalDateTime;

public record BookmarkedArticleResult(ArticleSummaryResult article, LocalDateTime bookmarkedAt) {
}
