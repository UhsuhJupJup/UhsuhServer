package uhsuhjupjup.backend.pipeline.notification.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSendLogService;
import uhsuhjupjup.backend.pipeline.notification.ui.dto.AdminEmailSendLogResponse;

import java.util.List;

@RestController
@RequestMapping("/api/admin/email-send-logs")
@RequiredArgsConstructor
public class AdminEmailSendLogController {

    private final EmailSendLogService emailSendLogService;

    @GetMapping
    public List<AdminEmailSendLogResponse> list(@AdminMember Member admin,
                                                @RequestParam(defaultValue = "50") int limit) {
        return emailSendLogService.recentLogs(limit).stream()
                .map(AdminEmailSendLogResponse::from)
                .toList();
    }
}
