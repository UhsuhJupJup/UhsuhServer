package uhsuhjupjup.backend.learningnote.application.dto;

import uhsuhjupjup.backend.learningnote.domain.LearningNote;

import java.util.List;

public record NoteResult(LearningNote note, List<String> keywords) {
}
