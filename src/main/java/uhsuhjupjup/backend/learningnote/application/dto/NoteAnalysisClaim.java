package uhsuhjupjup.backend.learningnote.application.dto;

public record NoteAnalysisClaim(Long noteId, long revision, String token, String title, String content) {
}
