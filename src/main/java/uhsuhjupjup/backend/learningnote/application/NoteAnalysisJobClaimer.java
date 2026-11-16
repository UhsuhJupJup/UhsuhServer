package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisClaim;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJob;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJobStatus;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisRetryPolicy;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteAnalysisJobRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class NoteAnalysisJobClaimer {

    private final NoteAnalysisJobRepository jobRepository;
    private final LearningNoteRepository learningNoteRepository;
    private final NoteAnalysisRetryPolicy retryPolicy;

    @Transactional
    public Optional<NoteAnalysisClaim> claim(Long noteId, Duration lease, LocalDateTime now) {
        String token = UUID.randomUUID().toString();
        int claimed = jobRepository.claim(noteId, token, now.plus(lease), now,
                NoteAnalysisJobStatus.PENDING, NoteAnalysisJobStatus.RUNNING);
        if (claimed == 0) {
            return Optional.empty();
        }
        NoteAnalysisJob job = jobRepository.findById(noteId).orElse(null);
        LearningNote note = learningNoteRepository.findById(noteId).orElse(null);
        if (job == null || note == null) {
            return Optional.empty();
        }
        if (!note.isRevision(job.getNoteRevision())) {
            job.supersede(token);
            return Optional.empty();
        }
        if (note.getAnalyzedAt() != null) {
            job.completeAlreadyAnalyzed(token, now);
            return Optional.empty();
        }
        if (job.failIfExhausted(token, now, retryPolicy)) {
            return Optional.empty();
        }
        return Optional.of(new NoteAnalysisClaim(noteId, job.getNoteRevision(), token,
                note.getTitle(), note.getContent()));
    }
}
