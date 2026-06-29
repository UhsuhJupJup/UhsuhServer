package uhsuhjupjup.backend.notification.infra;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.notification.application.EmailSendException;
import uhsuhjupjup.backend.notification.application.EmailSender;
import uhsuhjupjup.backend.notification.application.dto.EmailMessage;

@Component
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
class JavaMailEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final String from;

    JavaMailEmailSender(JavaMailSender javaMailSender,
                        @Value("${mail.from:어서줍줍 <noreply@eoseo.dev>}") String from) {
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
            javaMailSender.send(mimeMessage);
        } catch (MailException | MessagingException e) {
            throw new EmailSendException(message.to(), e);
        }
    }
}
