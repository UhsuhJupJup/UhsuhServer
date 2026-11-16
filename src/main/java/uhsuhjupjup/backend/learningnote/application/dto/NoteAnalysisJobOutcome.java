package uhsuhjupjup.backend.learningnote.application.dto;

public enum NoteAnalysisJobOutcome {
    COMPLETED,
    SUPERSEDED,
    LEASE_LOST,
    NOTE_DELETED,
    RETRY_SCHEDULED,
    FAILED
}
