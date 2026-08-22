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
import uhsuhjupjup.backend.emailsubscription.ui.dto.ManagedSubscriptionsResponse;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.keyword.ui.dto.KeywordResponse;
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
    private static final Duration MANAGE_TTL = Duration.ofMinutes(30);

    private final EmailSubscriberRepository emailSubscriberRepository;
    private final EmailSubscriptionRepository emailSubscriptionRepository;
    private final KeywordRepository keywordRepository;
    private final MemberRepository memberRepository;
    private final VerificationTokens verificationTokens;
    private final ManageLinkTokens manageLinkTokens;
    private final EmailSender emailSender;

    @Value("${email-subscription.verify-url:https://api.uhsuh.com/api/email-subscriptions/confirm}")
    private String verifyUrl;

    @Value("${email-subscription.confirm-redirect-url:https://www.uhsuh.com}")
    private String confirmRedirectUrl;

    @Value("${email-subscription.manage-url:https://www.uhsuh.com/manage}")
    private String manageUrl;

    /**
     * 비회원 이메일 구독 등록(신규 전용). member 이메일과 겹치면 거부(무겹침 불변식),
     * 이미 인증된 구독자면 거부(변경은 관리 링크로만). 미인증/신규면 구독을 교체하고 확인 메일을 보낸다.
     * 확인 메일 발송이 실패하면 트랜잭션이 롤백되어 반쪽 상태를 남기지 않는다(fail-closed).
     */
    @Transactional
    public void register(String email, List<Long> keywordIds) {
        if (memberRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_MEMBER);
        }

        List<Keyword> keywords = findKeywordsOrThrow(keywordIds);

        Optional<EmailSubscriber> existing = emailSubscriberRepository.findByEmail(email);
        if (existing.map(EmailSubscriber::isVerified).orElse(false)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_SUBSCRIBED);
        }

        EmailSubscriber subscriber = existing
                .orElseGet(() -> emailSubscriberRepository.save(EmailSubscriber.create(email)));
        replaceSubscriptions(subscriber, keywords);

        String token = verificationTokens.issue(subscriber.getId(), VERIFY_TTL);
        sendConfirmationEmail(subscriber.getEmail(), token);
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

    /**
     * 관리(매직 링크) 요청. 인증된 비회원 구독자가 존재할 때만 관리 토큰을 발급하고 메일을 보낸다.
     * 이메일 존재 여부를 노출하지 않도록(열거 방지) 어떤 경우든 정상 반환한다.
     */
    public void requestManageLink(String email) {
        emailSubscriberRepository.findByEmail(email)
                .filter(EmailSubscriber::isVerified)
                .ifPresent(subscriber -> {
                    String token = manageLinkTokens.issue(subscriber.getId(), MANAGE_TTL);
                    sendManageLinkEmail(subscriber.getEmail(), token);
                });
    }

    /** 관리 토큰으로 현재 구독 키워드를 조회한다(삭제 없이 peek). */
    @Transactional(readOnly = true)
    public ManagedSubscriptionsResponse getManagedSubscriptions(String token) {
        EmailSubscriber subscriber = resolveManaged(token);
        List<KeywordResponse> keywords = emailSubscriptionRepository.findByEmailSubscriberId(subscriber.getId())
                .stream()
                .map(EmailSubscription::getKeyword)
                .map(keyword -> new KeywordResponse(keyword.getId(), keyword.getName()))
                .toList();
        return new ManagedSubscriptionsResponse(subscriber.getEmail(), keywords);
    }

    /** 관리 토큰으로 구독 키워드를 교체한다. */
    @Transactional
    public void updateManagedSubscriptions(String token, List<Long> keywordIds) {
        EmailSubscriber subscriber = resolveManaged(token);
        List<Keyword> keywords = findKeywordsOrThrow(keywordIds);
        replaceSubscriptions(subscriber, keywords);
    }

    private EmailSubscriber resolveManaged(String token) {
        Long subscriberId = manageLinkTokens.peek(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_MANAGE_TOKEN));
        return emailSubscriberRepository.findById(subscriberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_MANAGE_TOKEN));
    }

    private List<Keyword> findKeywordsOrThrow(List<Long> keywordIds) {
        List<Keyword> keywords = keywordRepository.findAllById(keywordIds);
        if (keywords.isEmpty()) {
            throw new BusinessException(ErrorCode.KEYWORD_NOT_FOUND);
        }
        return keywords;
    }

    private void replaceSubscriptions(EmailSubscriber subscriber, List<Keyword> keywords) {
        emailSubscriptionRepository.deleteByEmailSubscriberId(subscriber.getId());
        for (Keyword keyword : keywords) {
            emailSubscriptionRepository.save(EmailSubscription.of(subscriber, keyword));
        }
    }

    private void sendConfirmationEmail(String email, String token) {
        String link = verifyUrl + "?token=" + token;
        emailSender.send(new EmailMessage(email, "[어서줍줍] 구독 확인 메일이에요 🐿️", buildConfirmationHtml(link), null));
    }

    private void sendManageLinkEmail(String email, String token) {
        String link = manageUrl + "?token=" + token;
        emailSender.send(new EmailMessage(email, "[어서줍줍] 구독 관리 링크예요 🐿️", buildManageHtml(link), null));
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

    private String buildManageHtml(String link) {
        return """
                <div style="max-width:480px;margin:0 auto;font-family:'Apple SD Gothic Neo',sans-serif;color:#2f3a2f;">
                  <h1 style="font-size:20px;">🐿️ 어서줍줍 구독 관리</h1>
                  <p>아래 버튼으로 구독 키워드를 변경할 수 있어요.</p>
                  <p style="margin:24px 0;">
                    <a href="%s" style="background:#5b7a4b;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;">구독 관리하기</a>
                  </p>
                  <p style="font-size:12px;color:#8a948a;">본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다. 링크는 30분 뒤 만료됩니다.</p>
                </div>
                """.formatted(link);
    }
}
