package uhsuhjupjup.backend.emailsubscription.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscription;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSender;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailSubscriptionService {

    private static final Duration VERIFY_TTL = Duration.ofHours(24);

    private final EmailSubscriberRepository emailSubscriberRepository;
    private final EmailSubscriptionRepository emailSubscriptionRepository;
    private final KeywordRepository keywordRepository;
    private final MemberRepository memberRepository;
    private final VerificationTokens verificationTokens;
    private final EmailSender emailSender;

    @Value("${email-subscription.verify-url:https://api.uhsuh.com/api/email-subscriptions/confirm}")
    private String verifyUrl;

    @Value("${email-subscription.confirm-redirect-url:https://www.uhsuh.com}")
    private String confirmRedirectUrl;

    /**
     * 비회원 이메일 구독 등록. member 테이블과 이메일이 겹치면 거부(무겹침 불변식).
     * 구독을 요청 키워드로 교체한 뒤, 아직 미인증이면 확인 토큰을 발급하고 확인 메일을 보낸다.
     * 확인 메일 발송이 실패하면 트랜잭션이 롤백되어 반쪽 상태를 남기지 않는다(fail-closed).
     */
    @Transactional
    public void register(String email, List<Long> keywordIds) {
        if (memberRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_MEMBER);
        }

        List<Keyword> keywords = keywordRepository.findAllById(keywordIds);
        if (keywords.isEmpty()) {
            throw new BusinessException(ErrorCode.KEYWORD_NOT_FOUND);
        }

        EmailSubscriber subscriber = emailSubscriberRepository.findByEmail(email)
                .orElseGet(() -> emailSubscriberRepository.save(EmailSubscriber.create(email)));

        emailSubscriptionRepository.deleteByEmailSubscriberId(subscriber.getId());
        for (Keyword keyword : keywords) {
            emailSubscriptionRepository.save(EmailSubscription.of(subscriber, keyword));
        }

        if (!subscriber.isVerified()) {
            String token = verificationTokens.issue(subscriber.getId(), VERIFY_TTL);
            sendConfirmationEmail(subscriber.getEmail(), token);
        }
    }

    /**
     * 확인 링크 처리. 토큰을 소비(1회용)하고 유효하면 구독자를 인증 상태로 만든다.
     * 링크 클릭 UX라 JSON 대신 프론트 랜딩 URL을 반환해 컨트롤러가 302로 리다이렉트한다.
     */
    @Transactional
    public String confirm(String token) {
        Optional<Long> subscriberId = verificationTokens.consume(token);
        if (subscriberId.isEmpty()) {
            return confirmRedirectUrl + "?verify=failed";
        }
        return emailSubscriberRepository.findById(subscriberId.get())
                .map(subscriber -> {
                    subscriber.verify(LocalDateTime.now());
                    return confirmRedirectUrl + "?verify=success";
                })
                .orElse(confirmRedirectUrl + "?verify=failed");
    }

    private void sendConfirmationEmail(String email, String token) {
        String link = verifyUrl + "?token=" + token;
        emailSender.send(new EmailMessage(email, "[어서줍줍] 구독 확인 메일이에요 🐿️", buildConfirmationHtml(link), null));
    }

    private String buildConfirmationHtml(String link) {
        return """
                <div style="max-width:480px;margin:0 auto;font-family:'Apple SD Gothic Neo',sans-serif;color:#2f3a2f;">
                  <h1 style="font-size:20px;">🐿️ 어서줍줍 구독 확인</h1>
                  <p>도토리 알림을 받으시려면 아래 버튼으로 이메일을 확인해 주세요.</p>
                  <p style="margin:24px 0;">
                    <a href="%s" style="background:#5b7a4b;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;">구독 확인하기</a>
                  </p>
                  <p style="font-size:12px;color:#8a948a;">본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다. 링크는 24시간 뒤 만료됩니다.</p>
                </div>
                """.formatted(link);
    }
}
