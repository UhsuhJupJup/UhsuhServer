package uhsuhjupjup.backend.learningnote.application.dto;

import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJob;

import java.util.List;

public record NoteResult(LearningNote note, List<String> keywords, NoteAnalysisState analysis) {

    public static NoteResult of(LearningNote note, List<String> keywords, NoteAnalysisJob job) {
        return new NoteResult(note, keywords, NoteAnalysisState.of(note, job));
    }
}
