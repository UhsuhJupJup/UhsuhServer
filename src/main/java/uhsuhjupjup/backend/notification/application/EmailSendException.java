package uhsuhjupjup.backend.notification.application;

public class EmailSendException extends RuntimeException {

    public EmailSendException(String to, Throwable cause) {
        super("이메일 발송 실패: " + to, cause);
    }
}
