package uhsuhjupjup.backend.oss.pipeline.grading.application;

public class IssueGradingException extends RuntimeException {

    public enum Reason {
        REFUSED,
        TRUNCATED,
        INVALID_OUTPUT,
        INVALID_INPUT,
        REJECTED,
        UNAVAILABLE
    }

    private final Reason reason;

    public IssueGradingException(Reason reason, String message) {
        this(reason, message, null);
    }

    public IssueGradingException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
