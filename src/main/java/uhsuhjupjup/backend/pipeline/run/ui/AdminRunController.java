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
import uhsuhjupjup.backend.pipeline.run.ui.dto.PipelineRunPageResponse;

@RestController
@RequestMapping("/api/admin/runs")
@RequiredArgsConstructor
public class AdminRunController implements AdminRunControllerApi {

    private final PipelineRunService pipelineRunService;
    private final PipelineScheduler pipelineScheduler;

    @Override
    @GetMapping
    public PipelineRunPageResponse list(@AdminMember Member admin,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return PipelineRunPageResponse.from(pipelineRunService.runs(page, size));
    }

    @Override
    @PostMapping("/notification")
    public NotificationResult triggerNotification(@AdminMember Member admin) {
        return pipelineScheduler.runNotificationNow();
    }
}
