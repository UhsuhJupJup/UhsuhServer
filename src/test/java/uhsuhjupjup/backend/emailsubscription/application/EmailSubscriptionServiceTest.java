package uhsuhjupjup.backend.emailsubscription.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSender;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSubscriptionServiceTest {

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;
    @Mock
    private EmailSubscriptionRepository emailSubscriptionRepository;
    @Mock
    private KeywordRepository keywordRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private VerificationTokens verificationTokens;
    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private EmailSubscriptionService emailSubscriptionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailSubscriptionService, "verifyUrl",
                "https://api.uhsuh.com/api/email-subscriptions/confirm");
        ReflectionTestUtils.setField(emailSubscriptionService, "confirmRedirectUrl", "https://www.uhsuh.com");
    }

    @Test
    void 회원_이메일로_등록하면_거부되고_메일도_안_보낸다() {
        given(memberRepository.findByEmail("m@example.com"))
                .willReturn(Optional.of(Member.create("google", "uid", "m@example.com")));

        assertThatThrownBy(() -> emailSubscriptionService.register("m@example.com", List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_MEMBER);

        verify(emailSender, never()).send(any());
    }

    @Test
    void 신규_등록시_구독을_저장하고_확인메일에_토큰_링크를_담아_보낸다() {
        given(memberRepository.findByEmail("new@example.com")).willReturn(Optional.empty());
        given(keywordRepository.findAllById(List.of(1L))).willReturn(List.of(Keyword.create("Redis")));
        given(emailSubscriberRepository.findByEmail("new@example.com")).willReturn(Optional.empty());
        given(emailSubscriberRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(verificationTokens.issue(any(), any(Duration.class))).willReturn("tok-123");

        emailSubscriptionService.register("new@example.com", List.of(1L));

        verify(emailSubscriptionRepository).save(any());
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        assertThat(captor.getValue().to()).isEqualTo("new@example.com");
        assertThat(captor.getValue().htmlBody()).contains("tok-123");
    }

    @Test
    void 유효한_토큰으로_확인하면_인증되고_성공_URL을_돌려준다() {
        EmailSubscriber subscriber = EmailSubscriber.create("c@example.com");
        given(verificationTokens.consume("tok-ok")).willReturn(Optional.of(10L));
        given(emailSubscriberRepository.findById(10L)).willReturn(Optional.of(subscriber));

        String redirect = emailSubscriptionService.confirm("tok-ok");

        assertThat(redirect).endsWith("verify=success");
        assertThat(subscriber.isVerified()).isTrue();
    }

    @Test
    void 만료된_토큰으로_확인하면_조회없이_실패_URL을_돌려준다() {
        given(verificationTokens.consume("expired")).willReturn(Optional.empty());

        String redirect = emailSubscriptionService.confirm("expired");

        assertThat(redirect).endsWith("verify=failed");
        verify(emailSubscriberRepository, never()).findById(any());
    }
}
