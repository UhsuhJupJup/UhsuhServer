package uhsuhjupjup.backend.emailsubscription.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.emailsubscription.application.AdminEmailSubscriptionService;
import uhsuhjupjup.backend.emailsubscription.ui.dto.AdminEmailSubscriberResponse;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.List;

@RestController
@RequestMapping("/api/admin/email-subscriptions")
@RequiredArgsConstructor
public class AdminEmailSubscriptionController {

    private final AdminEmailSubscriptionService adminEmailSubscriptionService;

    @GetMapping
    public List<AdminEmailSubscriberResponse> list(@AdminMember Member admin) {
        return adminEmailSubscriptionService.list();
    }
}
