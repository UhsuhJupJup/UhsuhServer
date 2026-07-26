package uhsuhjupjup.backend.archive.application.dto;

import uhsuhjupjup.backend.article.application.dto.ArticleSummaryResult;

import java.time.LocalDateTime;

public record SentArticleResult(ArticleSummaryResult article, LocalDateTime sentAt) {
}
