package uhsuhjupjup.backend.subscription.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.subscription.application.SubscriptionService;
import uhsuhjupjup.backend.subscription.ui.dto.UnsubscribeResponse;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UnsubscribeController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/api/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestParam String token) {
        String redirect = subscriptionService.unsubscribeByTokenForLanding(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirect))
                .build();
    }

    @PostMapping("/api/unsubscribe")
    public UnsubscribeResponse unsubscribeOneClick(@RequestParam String token) {
        subscriptionService.unsubscribeByToken(token);
        return new UnsubscribeResponse(true);
    }
}
