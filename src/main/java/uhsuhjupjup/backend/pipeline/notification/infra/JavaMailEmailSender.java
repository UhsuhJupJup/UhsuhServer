package uhsuhjupjup.backend.pipeline.notification.infra;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSendException;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSender;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;

@Component
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
class JavaMailEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final String from;

    JavaMailEmailSender(JavaMailSender javaMailSender,
                        @Value("${mail.from:어서줍줍 <noreply@uhsuh.com>}") String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
            if (message.unsubscribeUrl() != null) {
                mimeMessage.setHeader("List-Unsubscribe", "<" + message.unsubscribeUrl() + ">");
                mimeMessage.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
            javaMailSender.send(mimeMessage);
        } catch (MailException | MessagingException e) {
            throw new EmailSendException(message.to(), e, isPermanent(e));
        }
    }

    private boolean isPermanent(Exception e) {
        if (e instanceof MailSendException mailSendException) {
            for (Exception failure : mailSendException.getFailedMessages().values()) {
                if (hasInvalidAddress(failure)) {
                    return true;
                }
            }
        }
        return hasInvalidAddress(e);
    }

    private boolean hasInvalidAddress(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SendFailedException sendFailed) {
                Address[] invalid = sendFailed.getInvalidAddresses();
                if (invalid != null && invalid.length > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
