package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJob;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJobStatus;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisReason;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisRetryPolicy;
import uhsuhjupjup.backend.learningnote.infra.NoteAnalysisJobRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteAnalysisJobService {

    private final NoteAnalysisJobRepository jobRepository;
    private final NoteAnalysisJobEnqueuer enqueuer;
    private final NoteAnalysisRetryPolicy retryPolicy;

    public boolean enqueue(Long noteId, NoteAnalysisReason reason, LocalDateTime now) {
        int attempts = countsAsAttempt(reason) ? 1 : 0;
        LocalDateTime nextAttemptAt = switch (reason) {
            case CAPACITY_EXCEEDED, REQUEST_TIMEOUT, NOTE_CHANGED -> now;
            case CIRCUIT_OPEN -> retryPolicy.circuitOpenUntil(now);
            case AI_FAILED, JOB_TIMEOUT -> retryPolicy.nextAttemptAt(attempts, now);
        };
        try {
            return enqueuer.enqueue(noteId, reason, attempts, nextAttemptAt, now);
        } catch (DataIntegrityViolationException firstInsertRace) {
            log.info("노트 분석 작업 처음 저장 경합, 한 번 더 저장 noteId={}", noteId);
            return enqueuer.enqueue(noteId, reason, attempts, nextAttemptAt, now);
        }
    }

    public boolean retryFailed(Long noteId, long revision, LocalDateTime now) {
        return enqueuer.retryFailed(noteId, revision, now);
    }

    @Transactional(readOnly = true)
    public Optional<NoteAnalysisJob> find(Long noteId) {
        return jobRepository.findById(noteId);
    }

    @Transactional(readOnly = true)
    public Map<Long, NoteAnalysisJob> findByNoteIds(Collection<Long> noteIds) {
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        return jobRepository.findByNoteIdIn(noteIds).stream()
                .collect(Collectors.toMap(NoteAnalysisJob::getNoteId, Function.identity()));
    }

    @Transactional
    public void acknowledge(Long noteId, long revision, LocalDateTime now) {
        jobRepository.acknowledge(noteId, revision, now, NoteAnalysisJobStatus.DONE);
    }

    private static boolean countsAsAttempt(NoteAnalysisReason reason) {
        return switch (reason) {
            case CAPACITY_EXCEEDED, CIRCUIT_OPEN, NOTE_CHANGED -> false;
            case REQUEST_TIMEOUT, AI_FAILED, JOB_TIMEOUT -> true;
        };
    }
}
