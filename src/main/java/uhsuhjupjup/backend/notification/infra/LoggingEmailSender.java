package uhsuhjupjup.backend.notification.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.notification.application.EmailSender;
import uhsuhjupjup.backend.notification.application.dto.EmailMessage;

@Slf4j
@Component
@ConditionalOnProperty(name = "mail.enabled", havingValue = "false", matchIfMissing = true)
class LoggingEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        int bodyLength = message.htmlBody() == null ? 0 : message.htmlBody().length();
        log.info("[메일-로그] to={} subject={} 본문={}자", message.to(), message.subject(), bodyLength);
    }
}
