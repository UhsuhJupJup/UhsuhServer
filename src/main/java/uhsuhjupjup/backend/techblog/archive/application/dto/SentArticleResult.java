package uhsuhjupjup.backend.techblog.archive.application.dto;

import uhsuhjupjup.backend.techblog.article.application.dto.ArticleSummaryResult;

import java.time.LocalDateTime;

public record SentArticleResult(ArticleSummaryResult article, LocalDateTime sentAt) {
}
