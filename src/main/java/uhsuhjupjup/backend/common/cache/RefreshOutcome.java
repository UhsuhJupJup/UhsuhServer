package uhsuhjupjup.backend.common.cache;

public enum RefreshOutcome {
    STORED,
    LOCK_TIMEOUT,
    LOST_LOCK,
    REDIS_UNAVAILABLE,
    FAILED
}
