package uhsuhjupjup.backend.techblog.learningnote.application.dto;

import uhsuhjupjup.backend.techblog.learningnote.domain.LearningNote;

import java.util.List;

public record NoteResult(LearningNote note, List<String> keywords) {
}
