package uhsuhjupjup.backend.oss.github.application;

import java.util.OptionalInt;

public class GitHubClientException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED,
        UNAUTHORIZED,
        RATE_LIMITED,
        UNAVAILABLE,
        REJECTED,
        REDIRECT_REFUSED,
        INVALID_RESPONSE
    }

    private final Reason reason;
    private final Integer statusCode;

    public GitHubClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.statusCode = null;
    }

    public GitHubClientException(Reason reason, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.statusCode = statusCode;
    }

    public Reason getReason() {
        return reason;
    }

    public OptionalInt getStatusCode() {
        return statusCode == null ? OptionalInt.empty() : OptionalInt.of(statusCode);
    }
}
