package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisClaim;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisJobOutcome;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJob;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJobStatus;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisReason;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisRetryPolicy;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteAnalysisJobRepository;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
class NoteAnalysisJobRecorder {

    private final NoteAnalysisJobRepository jobRepository;
    private final LearningNoteRepository learningNoteRepository;
    private final NoteKeywordSaver noteKeywordSaver;
    private final NoteAnalysisRetryPolicy retryPolicy;

    @Transactional
    public NoteAnalysisJobOutcome complete(NoteAnalysisClaim claim, List<KeywordMatch> matches, LocalDateTime now) {
        LearningNote note = learningNoteRepository.findByIdForUpdate(claim.noteId()).orElse(null);
        NoteAnalysisJob job = note == null ? null : jobRepository.findByIdForUpdate(claim.noteId()).orElse(null);
        if (note == null || job == null) {
            return NoteAnalysisJobOutcome.NOTE_DELETED;
        }
        if (!job.holds(claim.token())) {
            return NoteAnalysisJobOutcome.LEASE_LOST;
        }
        if (!note.isRevision(claim.revision())) {
            job.supersede(claim.token());
            return NoteAnalysisJobOutcome.SUPERSEDED;
        }
        if (note.getAnalyzedAt() != null) {
            job.completeAlreadyAnalyzed(claim.token(), now);
            return NoteAnalysisJobOutcome.COMPLETED;
        }
        noteKeywordSaver.replaceKeywords(claim.noteId(), claim.revision(), matches, now);
        job.complete(claim.token(), now);
        return NoteAnalysisJobOutcome.COMPLETED;
    }

    @Transactional
    public NoteAnalysisJobOutcome fail(NoteAnalysisClaim claim, NoteAnalysisReason reason, LocalDateTime now) {
        NoteAnalysisJob job = jobRepository.findByIdForUpdate(claim.noteId()).orElse(null);
        if (job == null) {
            return NoteAnalysisJobOutcome.NOTE_DELETED;
        }
        boolean recorded = reason == NoteAnalysisReason.CIRCUIT_OPEN
                ? job.deferWhileCircuitOpen(claim.token(), now, retryPolicy)
                : job.retryLater(claim.token(), reason, now, retryPolicy);
        if (!recorded) {
            return NoteAnalysisJobOutcome.LEASE_LOST;
        }
        return job.getStatus() == NoteAnalysisJobStatus.FAILED
                ? NoteAnalysisJobOutcome.FAILED
                : NoteAnalysisJobOutcome.RETRY_SCHEDULED;
    }
}
