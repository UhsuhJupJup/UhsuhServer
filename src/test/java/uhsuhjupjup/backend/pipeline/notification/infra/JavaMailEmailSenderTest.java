package uhsuhjupjup.backend.pipeline.notification.infra;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSendException;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JavaMailEmailSenderTest {

    private JavaMailSender javaMailSender;
    private JavaMailEmailSender sender;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        javaMailSender = mock(JavaMailSender.class);
        sender = new JavaMailEmailSender(javaMailSender, "어서줍줍 <noreply@test.dev>");
        mimeMessage = new MimeMessage((Session) null);
        given(javaMailSender.createMimeMessage()).willReturn(mimeMessage);
    }

    @Test
    void send_buildsAndSendsMime() {
        sender.send(new EmailMessage("user@test.com", "제목", "<p>본문</p>",
                "https://api.uhsuh.com/api/unsubscribe?token=tok-1"));

        verify(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_setsOneClickUnsubscribeHeaders() throws Exception {
        sender.send(new EmailMessage("user@test.com", "제목", "<p>본문</p>",
                "https://api.uhsuh.com/api/unsubscribe?token=tok-1"));

        assertThat(mimeMessage.getHeader("List-Unsubscribe", null))
                .isEqualTo("<https://api.uhsuh.com/api/unsubscribe?token=tok-1>");
        assertThat(mimeMessage.getHeader("List-Unsubscribe-Post", null))
                .isEqualTo("List-Unsubscribe=One-Click");
    }

    @Test
    void send_withoutUnsubscribeUrl_omitsHeaders() throws Exception {
        sender.send(new EmailMessage("user@test.com", "제목", "<p>본문</p>", null));

        assertThat(mimeMessage.getHeader("List-Unsubscribe", null)).isNull();
        assertThat(mimeMessage.getHeader("List-Unsubscribe-Post", null)).isNull();
    }

    @Test
    void send_whenMailFails_throwsEmailSendException() {
        willThrow(new MailSendException("boom")).given(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(new EmailMessage("user@test.com", "제목", "<p>본문</p>", null)))
                .isInstanceOf(EmailSendException.class);
    }
}
