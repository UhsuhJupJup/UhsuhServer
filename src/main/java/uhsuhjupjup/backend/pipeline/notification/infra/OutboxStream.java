package uhsuhjupjup.backend.pipeline.notification.infra;

final class OutboxStream {

    static final String KEY = "uhsuh:notification:outbox";
    static final String GROUP = "mail-senders";
    static final String OUTBOX_ID_FIELD = "outboxId";

    private OutboxStream() {
    }
}
