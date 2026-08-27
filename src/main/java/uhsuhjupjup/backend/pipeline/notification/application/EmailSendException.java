package uhsuhjupjup.backend.pipeline.notification.application;

public class EmailSendException extends RuntimeException {

    private final boolean permanent;

    public EmailSendException(String to, Throwable cause, boolean permanent) {
        super("이메일 발송 실패: " + to, cause);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
