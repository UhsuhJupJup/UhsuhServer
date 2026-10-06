package uhsuhjupjup.backend.config.llm;

public class LlmRetryAbortedException extends RuntimeException {

    LlmRetryAbortedException(String message) {
        super(message);
    }

    LlmRetryAbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}
