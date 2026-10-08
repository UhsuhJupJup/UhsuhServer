package uhsuhjupjup.backend.oss.pipeline.grading.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.pipeline.grading.application.OssIssueGradingService;
import uhsuhjupjup.backend.oss.pipeline.grading.ui.dto.AdminOssIssueGradingResponse;

@RestController
@RequestMapping("/api/admin/oss/repos")
@RequiredArgsConstructor
public class AdminOssIssueGradingController implements AdminOssIssueGradingControllerApi {

    private final OssIssueGradingService ossIssueGradingService;

    @Override
    @PostMapping("/{repoId}/issues/grade")
    public AdminOssIssueGradingResponse gradeIssues(@AdminMember Member admin, @PathVariable Long repoId,
                                                    @RequestParam(defaultValue = "5") int limit) {
        return AdminOssIssueGradingResponse.from(ossIssueGradingService.gradeRepo(repoId, limit));
    }
}
