package uhsuhjupjup.backend.learningnote.ui.dto;

import uhsuhjupjup.backend.learningnote.application.dto.NoteRecommendationView;

import java.util.List;

public record NoteRecommendationResponse(String status, List<String> keywords,
                                         List<RecommendedArticleResponse> articles) {

    public static NoteRecommendationResponse from(NoteRecommendationView view) {
        return new NoteRecommendationResponse(
                view.status().name(),
                view.result().keywords(),
                view.result().articles().stream().map(RecommendedArticleResponse::from).toList()
        );
    }
}
