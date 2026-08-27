package uhsuhjupjup.backend.pipeline.notification.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxAdminService;
import uhsuhjupjup.backend.pipeline.notification.ui.dto.AdminOutboxResponse;

@RestController
@RequestMapping("/api/admin/outbox")
@RequiredArgsConstructor
public class AdminOutboxController {

    private final OutboxAdminService outboxAdminService;

    @GetMapping
    public AdminOutboxResponse summary(@AdminMember Member admin,
                                       @RequestParam(defaultValue = "50") int failedLimit) {
        return AdminOutboxResponse.from(outboxAdminService.summary(failedLimit));
    }

    @PostMapping("/{id}/requeue")
    public void requeue(@AdminMember Member admin, @PathVariable Long id) {
        outboxAdminService.requeue(id);
    }
}
