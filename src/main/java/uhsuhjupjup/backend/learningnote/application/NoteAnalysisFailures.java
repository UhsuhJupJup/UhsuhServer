package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisReason;

import java.util.concurrent.TimeoutException;

final class NoteAnalysisFailures {

    private static final int MAX_CAUSE_DEPTH = 20;

    private NoteAnalysisFailures() {
    }

    static NoteAnalysisReason reasonOf(Throwable error) {
        if (error instanceof TimeoutException) {
            return NoteAnalysisReason.JOB_TIMEOUT;
        }
        return circuitOpen(error) ? NoteAnalysisReason.CIRCUIT_OPEN : NoteAnalysisReason.AI_FAILED;
    }

    private static boolean circuitOpen(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof CallNotPermittedException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
