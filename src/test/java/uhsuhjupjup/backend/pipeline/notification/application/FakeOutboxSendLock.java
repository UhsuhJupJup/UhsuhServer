package uhsuhjupjup.backend.pipeline.notification.application;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeOutboxSendLock implements OutboxSendLock {

    private final Set<Long> held = ConcurrentHashMap.newKeySet();

    public void holdByOtherServer(Long outboxId) {
        held.add(outboxId);
    }

    public boolean isHeld(Long outboxId) {
        return held.contains(outboxId);
    }

    public void clear() {
        held.clear();
    }

    @Override
    public Optional<Lease> tryAcquire(Long outboxId) {
        if (!held.add(outboxId)) {
            return Optional.empty();
        }
        return Optional.of(() -> held.remove(outboxId));
    }
}
