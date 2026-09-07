package uhsuhjupjup.backend.pipeline.notification.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxAdminService;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.ui.dto.AdminEmailSendDailyResponse;
import uhsuhjupjup.backend.pipeline.notification.ui.dto.AdminEmailSendLogResponse;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/email-send-logs")
@RequiredArgsConstructor
public class AdminEmailSendLogController implements AdminEmailSendLogControllerApi {

    private final OutboxAdminService outboxAdminService;

    @Override
    @GetMapping
    public List<AdminEmailSendLogResponse> list(@AdminMember Member admin,
                                                @RequestParam(defaultValue = "50") int limit,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<NotificationOutbox> rows =
                date != null ? outboxAdminService.sentOn(date) : outboxAdminService.recentSent(limit);
        return rows.stream()
                .map(AdminEmailSendLogResponse::from)
                .toList();
    }

    @Override
    @GetMapping("/daily")
    public List<AdminEmailSendDailyResponse> daily(@AdminMember Member admin) {
        return outboxAdminService.dailySummary().stream()
                .map(AdminEmailSendDailyResponse::from)
                .toList();
    }
}
