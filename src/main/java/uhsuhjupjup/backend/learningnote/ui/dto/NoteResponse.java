package uhsuhjupjup.backend.learningnote.ui.dto;

import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisState;
import uhsuhjupjup.backend.learningnote.application.dto.NoteResult;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;

import java.time.LocalDateTime;
import java.util.List;

public record NoteResponse(
        Long id,
        String title,
        String content,
        List<String> keywords,
        LocalDateTime analyzedAt,
        String analysisStatus,
        boolean recommendationReady,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NoteResponse from(LearningNote note) {
        return from(note, List.of(), NoteAnalysisState.of(note, null));
    }

    public static NoteResponse from(NoteResult result) {
        return from(result.note(), result.keywords(), result.analysis());
    }

    private static NoteResponse from(LearningNote note, List<String> keywords, NoteAnalysisState analysis) {
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                keywords,
                note.getAnalyzedAt(),
                analysis.status().name(),
                analysis.recommendationReady(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
