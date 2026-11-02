package uhsuhjupjup.backend.pipeline.notification.application;

import java.util.Optional;

public interface OutboxSendLock {

    Optional<Lease> tryAcquire(Long outboxId);

    interface Lease extends AutoCloseable {

        @Override
        void close();
    }
}
