package uhsuhjupjup.backend.subscription.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.subscription.application.SubscriptionService;
import uhsuhjupjup.backend.subscription.ui.dto.UnsubscribeResponse;

@RestController
@RequiredArgsConstructor
public class UnsubscribeController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/api/unsubscribe")
    public UnsubscribeResponse unsubscribe(@RequestParam String token) {
        subscriptionService.unsubscribeByToken(token);
        return new UnsubscribeResponse(true);
    }
}
