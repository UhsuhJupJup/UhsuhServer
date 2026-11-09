package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import uhsuhjupjup.backend.config.NoteAsyncConfig;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisClaim;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisJobOutcome;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJobStatus;
import uhsuhjupjup.backend.learningnote.infra.NoteAnalysisJobRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class NoteAnalysisJobRunner {

    private final NoteAnalysisJobRepository jobRepository;
    private final NoteAnalysisJobClaimer claimer;
    private final NoteAnalysisJobRecorder recorder;
    private final NoteAnalyzer noteAnalyzer;
    private final Bulkhead aiBulkhead;
    private final Scheduler noteDbScheduler;
    private final Duration lease;
    private final Duration jobTimeout;

    public NoteAnalysisJobRunner(NoteAnalysisJobRepository jobRepository,
                                 NoteAnalysisJobClaimer claimer,
                                 NoteAnalysisJobRecorder recorder,
                                 NoteAnalyzer noteAnalyzer,
                                 BulkheadRegistry bulkheadRegistry,
                                 @Qualifier(NoteAsyncConfig.NOTE_DB_SCHEDULER) Scheduler noteDbScheduler,
                                 @Value("${note.analysis.job.lease:PT3M}") Duration lease,
                                 @Value("${note.analysis.job.timeout:PT2M}") Duration jobTimeout) {
        if (lease.compareTo(jobTimeout) <= 0) {
            throw new IllegalArgumentException("노트 분석 작업 임대 " + lease + "는 작업 상한 " + jobTimeout + "보다 길어야 합니다");
        }
        this.jobRepository = jobRepository;
        this.claimer = claimer;
        this.recorder = recorder;
        this.noteAnalyzer = noteAnalyzer;
        this.aiBulkhead = bulkheadRegistry.bulkhead(NoteAnalyzer.AI_BULKHEAD);
        this.noteDbScheduler = noteDbScheduler;
        this.lease = lease;
        this.jobTimeout = jobTimeout;
    }

    public int dispatch(LocalDateTime now) {
        int capacity = aiBulkhead.getMetrics().getAvailableConcurrentCalls();
        if (capacity <= 0) {
            return 0;
        }
        int started = 0;
        for (Long noteId : candidates(now, capacity)) {
            if (!aiBulkhead.tryAcquirePermission()) {
                break;
            }
            Optional<NoteAnalysisClaim> claim = claimHoldingPermit(noteId, now);
            if (claim.isEmpty()) {
                aiBulkhead.releasePermission();
                continue;
            }
            run(claim.get());
            started++;
        }
        return started;
    }

    private Set<Long> candidates(LocalDateTime now, int capacity) {
        Set<Long> noteIds = new LinkedHashSet<>(jobRepository.findExpiredLeases(
                NoteAnalysisJobStatus.RUNNING, now, PageRequest.of(0, capacity)));
        if (noteIds.size() < capacity) {
            noteIds.addAll(jobRepository.findDueAttempts(
                    NoteAnalysisJobStatus.PENDING, now, PageRequest.of(0, capacity - noteIds.size())));
        }
        return noteIds;
    }

    private Optional<NoteAnalysisClaim> claimHoldingPermit(Long noteId, LocalDateTime now) {
        try {
            return claimer.claim(noteId, lease, now);
        } catch (RuntimeException e) {
            log.warn("노트 분석 작업 선점 실패 noteId={} 사유={}", noteId, e.toString());
            return Optional.empty();
        }
    }

    private void run(NoteAnalysisClaim claim) {
        Mono.defer(() -> noteAnalyzer.classifyAsync(claim.title(), claim.content()))
                .timeout(jobTimeout)
                .publishOn(noteDbScheduler)
                .map(matches -> recorder.complete(claim, matches, LocalDateTime.now()))
                .onErrorResume(error -> Mono.fromCallable(() -> {
                    log.warn("노트 분석 작업 실패 noteId={} 사유={}", claim.noteId(), error.toString());
                    return recorder.fail(claim, NoteAnalysisFailures.reasonOf(error), LocalDateTime.now());
                }).subscribeOn(noteDbScheduler))
                .doFinally(signal -> aiBulkhead.onComplete())
                .subscribe(outcome -> logOutcome(claim, outcome),
                        error -> log.warn("노트 분석 작업 결과 기록 실패 noteId={} 사유={}", claim.noteId(), error.toString()));
    }

    private static void logOutcome(NoteAnalysisClaim claim, NoteAnalysisJobOutcome outcome) {
        log.info("노트 분석 작업 처리 noteId={} 결과={}", claim.noteId(), outcome);
    }
}
