package uhsuhjupjup.backend.pipeline.notification.infra;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSender;

import java.util.Properties;

@Configuration
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
class MailConfig {

    @Bean
    EmailSender sesEmailSender(JavaMailSender javaMailSender,
                               @Value("${mail.from:어서줍줍 <noreply@uhsuh.com>}") String from) {
        return new JavaMailEmailSender(javaMailSender, from);
    }

    @Bean
    EmailSender gmailEmailSender(@Value("${mail.gmail.host:smtp.gmail.com}") String host,
                                 @Value("${mail.gmail.port:587}") int port,
                                 @Value("${mail.gmail.username}") String username,
                                 @Value("${mail.gmail.password}") String password,
                                 @Value("${mail.from:어서줍줍 <noreply@uhsuh.com>}") String from) {
        JavaMailSenderImpl gmailMailSender = new JavaMailSenderImpl();
        gmailMailSender.setHost(host);
        gmailMailSender.setPort(port);
        gmailMailSender.setUsername(username);
        gmailMailSender.setPassword(password);
        gmailMailSender.setDefaultEncoding("UTF-8");
        Properties properties = gmailMailSender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        return new JavaMailEmailSender(gmailMailSender, from);
    }

    @Bean
    @Primary
    EmailSender failoverEmailSender(@Qualifier("sesEmailSender") EmailSender primary,
                                    @Qualifier("gmailEmailSender") EmailSender fallback,
                                    @Value("${mail.fallback.enabled:true}") boolean fallbackEnabled) {
        return new FailoverEmailSender(primary, fallback, fallbackEnabled);
    }
}
