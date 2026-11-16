package uhsuhjupjup.backend.pipeline.notification.domain;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
