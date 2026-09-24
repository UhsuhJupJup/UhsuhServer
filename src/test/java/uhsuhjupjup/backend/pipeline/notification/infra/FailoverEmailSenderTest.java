package uhsuhjupjup.backend.pipeline.notification.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSendException;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSender;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FailoverEmailSenderTest {

    private final EmailMessage message =
            new EmailMessage("user@test.com", "제목", "<p>본문</p>", null);

    private EmailSender primary;
    private EmailSender fallback;

    @BeforeEach
    void setUp() {
        primary = mock(EmailSender.class);
        fallback = mock(EmailSender.class);
    }

    private EmailSendException temporaryFailure() {
        return new EmailSendException(message.to(), new RuntimeException("일시 오류"), false);
    }

    private EmailSendException permanentFailure() {
        return new EmailSendException(message.to(), new RuntimeException("잘못된 주소"), true);
    }

    @Test
    void SES가_성공하면_Gmail_폴백을_호출하지_않는다() {
        FailoverEmailSender sender = new FailoverEmailSender(primary, fallback, true);

        sender.send(message);

        verify(primary).send(message);
        verify(fallback, never()).send(any(EmailMessage.class));
    }

    @Test
    void SES가_일시_실패하면_Gmail로_폴백한다() {
        willThrow(temporaryFailure()).given(primary).send(message);
        FailoverEmailSender sender = new FailoverEmailSender(primary, fallback, true);

        sender.send(message);

        verify(fallback).send(message);
    }

    @Test
    void SES가_영구_실패하면_폴백없이_예외를_던진다() {
        willThrow(permanentFailure()).given(primary).send(message);
        FailoverEmailSender sender = new FailoverEmailSender(primary, fallback, true);

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(EmailSendException.class);

        verify(fallback, never()).send(any(EmailMessage.class));
    }

    @Test
    void Gmail_폴백도_실패하면_예외가_전파된다() {
        willThrow(temporaryFailure()).given(primary).send(message);
        willThrow(permanentFailure()).given(fallback).send(message);
        FailoverEmailSender sender = new FailoverEmailSender(primary, fallback, true);

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(EmailSendException.class);

        verify(fallback).send(message);
    }

    @Test
    void 폴백이_비활성이면_SES_일시_실패도_폴백없이_예외를_던진다() {
        willThrow(temporaryFailure()).given(primary).send(message);
        FailoverEmailSender sender = new FailoverEmailSender(primary, fallback, false);

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(EmailSendException.class);

        verify(fallback, never()).send(any(EmailMessage.class));
    }
}
