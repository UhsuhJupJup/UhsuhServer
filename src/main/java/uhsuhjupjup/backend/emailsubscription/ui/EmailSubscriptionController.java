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
public class EmailSubscriptionController implements EmailSubscriptionControllerApi {

    private final EmailSubscriptionService emailSubscriptionService;
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@RequestBody @Valid EmailSubscriptionRequest request) {
        emailSubscriptionService.register(request.email(), request.keywordIds());
    }
    @Override
    @GetMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestParam String token) {
        String redirect = emailSubscriptionService.confirm(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirect))
                .build();
    }
    @Override
    @PostMapping("/manage-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestManageLink(@RequestBody @Valid ManageLinkRequest request) {
        emailSubscriptionService.requestManageLink(request.email());
    }
    @Override
    @GetMapping("/manage")
    public ManagedSubscriptionsResponse getManaged(@RequestParam String token) {
        return emailSubscriptionService.getManagedSubscriptions(token);
    }
    @Override
    @PutMapping("/manage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateManaged(@RequestParam String token, @RequestBody @Valid ManageSubscriptionsRequest request) {
        emailSubscriptionService.updateManagedSubscriptions(token, request.keywordIds());
    }
}
