package uhsuhjupjup.backend.learningnote.ui.dto;

import uhsuhjupjup.backend.learningnote.application.dto.NoteRecommendationResult;

import java.util.List;

public record NoteRecommendationResponse(List<String> keywords, List<RecommendedArticleResponse> articles) {

    public static NoteRecommendationResponse from(NoteRecommendationResult result) {
        return new NoteRecommendationResponse(
                result.keywords(),
                result.articles().stream().map(RecommendedArticleResponse::from).toList()
        );
    }
}
