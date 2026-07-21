package uhsuhjupjup.backend.learningnote.ui.dto;

import uhsuhjupjup.backend.learningnote.domain.LearningNote;

import java.time.LocalDateTime;

public record NoteResponse(
        Long id,
        String title,
        String content,
        LocalDateTime analyzedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NoteResponse from(LearningNote note) {
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getAnalyzedAt(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
