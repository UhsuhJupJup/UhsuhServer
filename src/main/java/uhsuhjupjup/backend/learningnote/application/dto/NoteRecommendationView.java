package uhsuhjupjup.backend.learningnote.application.dto;

import java.util.List;

public record NoteRecommendationView(NoteRecommendationStatus status, NoteRecommendationResult result) {

    public static NoteRecommendationView ready(NoteRecommendationResult result) {
        return new NoteRecommendationView(NoteRecommendationStatus.READY, result);
    }

    public static NoteRecommendationView pending() {
        return new NoteRecommendationView(NoteRecommendationStatus.PENDING, empty());
    }

    public static NoteRecommendationView failed() {
        return new NoteRecommendationView(NoteRecommendationStatus.FAILED, empty());
    }

    private static NoteRecommendationResult empty() {
        return new NoteRecommendationResult(List.of(), List.of());
    }
}
