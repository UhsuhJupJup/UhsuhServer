package uhsuhjupjup.backend.learningnote.application.dto;

import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJob;

public record NoteAnalysisState(NoteAnalysisStatus status, boolean recommendationReady) {

    public static NoteAnalysisState of(LearningNote note, NoteAnalysisJob job) {
        long revision = note.getContentRevision();
        if (note.getAnalyzedAt() != null) {
            return new NoteAnalysisState(NoteAnalysisStatus.ANALYZED,
                    job != null && job.isUnacknowledgedResultFor(revision));
        }
        if (job != null && job.isActiveFor(revision)) {
            return new NoteAnalysisState(NoteAnalysisStatus.PENDING, false);
        }
        if (job != null && job.isFailedFor(revision)) {
            return new NoteAnalysisState(NoteAnalysisStatus.FAILED, false);
        }
        return new NoteAnalysisState(NoteAnalysisStatus.NONE, false);
    }
}
