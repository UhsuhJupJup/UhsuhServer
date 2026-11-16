package uhsuhjupjup.backend.pipeline.notification.application;

public interface OutboxMessageQueue {

    void publish(Long outboxId);
}
