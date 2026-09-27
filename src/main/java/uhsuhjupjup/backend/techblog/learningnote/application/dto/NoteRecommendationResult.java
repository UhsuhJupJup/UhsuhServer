package uhsuhjupjup.backend.techblog.learningnote.application.dto;

import java.util.List;

public record NoteRecommendationResult(List<String> keywords, List<RecommendedArticleResult> articles) {
}
