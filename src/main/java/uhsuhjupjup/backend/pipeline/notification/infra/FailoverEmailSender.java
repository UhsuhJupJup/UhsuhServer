package uhsuhjupjup.backend.pipeline.notification.infra;

import lombok.extern.slf4j.Slf4j;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSendException;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSender;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;

@Slf4j
class FailoverEmailSender implements EmailSender {

    private final EmailSender primary;
    private final EmailSender fallback;
    private final boolean fallbackEnabled;

    FailoverEmailSender(EmailSender primary, EmailSender fallback, boolean fallbackEnabled) {
        this.primary = primary;
        this.fallback = fallback;
        this.fallbackEnabled = fallbackEnabled;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            primary.send(message);
        } catch (EmailSendException e) {
            if (e.isPermanent() || !fallbackEnabled) {
                throw e;
            }
            log.warn("SES 발송 일시 실패, Gmail 폴백 시도 to={} 사유={}", message.to(), e.getMessage());
            fallback.send(message);
        }
    }
}
