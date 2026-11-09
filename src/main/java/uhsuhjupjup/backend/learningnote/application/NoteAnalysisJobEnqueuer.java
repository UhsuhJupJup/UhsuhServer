package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJob;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisReason;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteAnalysisJobRepository;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class NoteAnalysisJobEnqueuer {

    private final NoteAnalysisJobRepository jobRepository;
    private final LearningNoteRepository learningNoteRepository;

    @Transactional
    public boolean enqueue(Long noteId, NoteAnalysisReason reason, int attempts, LocalDateTime nextAttemptAt,
                           LocalDateTime now) {
        LearningNote note = learningNoteRepository.findById(noteId).orElse(null);
        if (note == null || note.getAnalyzedAt() != null) {
            return false;
        }
        long revision = note.getContentRevision();
        if (!jobRepository.existsById(noteId)) {
            jobRepository.saveAndFlush(NoteAnalysisJob.queue(noteId, revision, reason, attempts, nextAttemptAt, now));
            return true;
        }
        return jobRepository.findByIdForUpdate(noteId)
                .map(job -> job.requeue(revision, reason, attempts, nextAttemptAt, now))
                .orElse(false);
    }

    @Transactional
    public boolean retryFailed(Long noteId, long revision, LocalDateTime now) {
        return jobRepository.findByIdForUpdate(noteId)
                .map(job -> job.retryFailed(revision, now))
                .orElse(false);
    }
}
