package uhsuhjupjup.backend.oss.pipeline.sync.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.pipeline.sync.application.OssIssueSyncAdminService;
import uhsuhjupjup.backend.oss.pipeline.sync.ui.dto.AdminOssIssueSyncResponse;

@RestController
@RequestMapping("/api/admin/oss/repos")
@RequiredArgsConstructor
public class AdminOssIssueSyncController implements AdminOssIssueSyncControllerApi {

    private final OssIssueSyncAdminService ossIssueSyncAdminService;

    @Override
    @PostMapping("/{repoId}/issues/sync")
    public AdminOssIssueSyncResponse syncIssues(@AdminMember Member admin, @PathVariable Long repoId) {
        return AdminOssIssueSyncResponse.from(ossIssueSyncAdminService.syncRepo(repoId));
    }
}
