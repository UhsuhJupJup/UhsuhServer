package uhsuhjupjup.backend.emailsubscription.ui;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.emailsubscription.application.EmailSubscriptionService;
import uhsuhjupjup.backend.emailsubscription.ui.dto.EmailSubscriptionRequest;
import uhsuhjupjup.backend.emailsubscription.ui.dto.ManageLinkRequest;
import uhsuhjupjup.backend.emailsubscription.ui.dto.ManageSubscriptionsRequest;
import uhsuhjupjup.backend.emailsubscription.ui.dto.ManagedSubscriptionsResponse;

import java.net.URI;

@RestController
@RequestMapping("/api/email-subscriptions")
@RequiredArgsConstructor
public class EmailSubscriptionController {

    private final EmailSubscriptionService emailSubscriptionService;

    /** 비회원 이메일 구독 등록(로그인 불필요). 확인 메일을 보내고 202를 반환한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@RequestBody @Valid EmailSubscriptionRequest request) {
        emailSubscriptionService.register(request.email(), request.keywordIds());
    }

    /** 확인 메일 링크 진입점. 토큰 검증 후 프론트 랜딩으로 302 리다이렉트한다. */
    @GetMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestParam String token) {
        String redirect = emailSubscriptionService.confirm(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirect))
                .build();
    }

    /** 관리(매직 링크) 요청. 이메일 존재 여부와 무관하게 202(열거 방지). */
    @PostMapping("/manage-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestManageLink(@RequestBody @Valid ManageLinkRequest request) {
        emailSubscriptionService.requestManageLink(request.email());
    }

    /** 관리 토큰으로 현재 구독 키워드를 조회한다. */
    @GetMapping("/manage")
    public ManagedSubscriptionsResponse getManaged(@RequestParam String token) {
        return emailSubscriptionService.getManagedSubscriptions(token);
    }

    /** 관리 토큰으로 구독 키워드를 교체한다. */
    @PutMapping("/manage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateManaged(@RequestParam String token, @RequestBody @Valid ManageSubscriptionsRequest request) {
        emailSubscriptionService.updateManagedSubscriptions(token, request.keywordIds());
    }
}
