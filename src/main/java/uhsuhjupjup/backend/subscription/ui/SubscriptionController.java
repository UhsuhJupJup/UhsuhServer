package uhsuhjupjup.backend.subscription.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.LoginMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.subscription.application.SubscriptionService;
import uhsuhjupjup.backend.subscription.ui.dto.SubscriptionUpdateRequest;
import uhsuhjupjup.backend.subscription.ui.dto.SubscriptionsResponse;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public SubscriptionsResponse mySubscriptions(@LoginMember Member member) {
        return SubscriptionsResponse.from(subscriptionService.getMySubscriptions(member.getId()));
    }

    @PutMapping
    public SubscriptionsResponse replace(@LoginMember Member member, @RequestBody SubscriptionUpdateRequest request) {
        return SubscriptionsResponse.from(
                subscriptionService.replaceSubscriptions(member, request.topicIds(), request.keywordIds()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribeAll(@LoginMember Member member) {
        subscriptionService.unsubscribeAll(member.getId());
    }
}
