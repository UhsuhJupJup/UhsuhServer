package uhsuhjupjup.backend.archive.application.dto;

import uhsuhjupjup.backend.techblog.article.application.dto.ArticleSummaryResult;

import java.time.LocalDateTime;

public record BookmarkedArticleResult(ArticleSummaryResult article, LocalDateTime bookmarkedAt) {
}
