package uhsuhjupjup.backend.emailsubscription.ui;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.emailsubscription.application.EmailSubscriptionService;
import uhsuhjupjup.backend.emailsubscription.ui.dto.EmailSubscriptionRequest;

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
}
