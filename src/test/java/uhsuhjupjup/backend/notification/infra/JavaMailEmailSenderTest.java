package uhsuhjupjup.backend.notification.infra;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import uhsuhjupjup.backend.notification.application.EmailSendException;
import uhsuhjupjup.backend.notification.application.dto.EmailMessage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JavaMailEmailSenderTest {

    private JavaMailSender javaMailSender;
    private JavaMailEmailSender sender;

    @BeforeEach
    void setUp() {
        javaMailSender = mock(JavaMailSender.class);
        sender = new JavaMailEmailSender(javaMailSender, "어서줍줍 <noreply@test.dev>");
        given(javaMailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
    }

    @Test
    void send_buildsAndSendsMime() {
        sender.send(new EmailMessage("user@test.com", "제목", "<p>본문</p>"));

        verify(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_whenMailFails_throwsEmailSendException() {
        willThrow(new MailSendException("boom")).given(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(new EmailMessage("user@test.com", "제목", "<p>본문</p>")))
                .isInstanceOf(EmailSendException.class);
    }
}
