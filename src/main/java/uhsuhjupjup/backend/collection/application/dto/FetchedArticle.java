package uhsuhjupjup.backend.collection.application.dto;

import java.time.LocalDateTime;

public record FetchedArticle(String title, String url, LocalDateTime publishedAt) {
}
