package uhsuhjupjup.backend.pipeline.run.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.PipelineScheduler;
import uhsuhjupjup.backend.pipeline.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.pipeline.run.application.PipelineRunService;
import uhsuhjupjup.backend.pipeline.run.ui.dto.PipelineRunResponse;

import java.util.List;

@RestController
@RequestMapping("/api/admin/runs")
@RequiredArgsConstructor
public class AdminRunController {

    private final PipelineRunService pipelineRunService;
    private final PipelineScheduler pipelineScheduler;

    @GetMapping
    public List<PipelineRunResponse> list(@AdminMember Member admin,
                                          @RequestParam(defaultValue = "30") int limit) {
        return pipelineRunService.recentRuns(limit).stream()
                .map(PipelineRunResponse::from)
                .toList();
    }

    @PostMapping("/notification")
    public NotificationResult triggerNotification(@AdminMember Member admin) {
        return pipelineScheduler.runNotificationNow();
    }
}
