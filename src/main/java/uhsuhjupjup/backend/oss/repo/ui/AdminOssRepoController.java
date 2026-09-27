package uhsuhjupjup.backend.oss.repo.ui;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.repo.application.OssRepoCategoryService;
import uhsuhjupjup.backend.oss.repo.application.OssRepoRegistrationService;
import uhsuhjupjup.backend.oss.repo.ui.dto.AdminOssRepoResponse;
import uhsuhjupjup.backend.oss.repo.ui.dto.OssRepoRegisterRequest;
import uhsuhjupjup.backend.oss.repo.ui.dto.OssRepoUpdateRequest;

@RestController
@RequestMapping("/api/admin/oss/repos")
@RequiredArgsConstructor
public class AdminOssRepoController implements AdminOssRepoControllerApi {

    private final OssRepoRegistrationService ossRepoRegistrationService;
    private final OssRepoCategoryService ossRepoCategoryService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminOssRepoResponse register(@AdminMember Member admin,
                                         @Valid @RequestBody OssRepoRegisterRequest request) {
        return AdminOssRepoResponse.from(ossRepoRegistrationService.register(request.fullName()));
    }

    @Override
    @PatchMapping("/{repoId}")
    public AdminOssRepoResponse update(@AdminMember Member admin, @PathVariable Long repoId,
                                       @Valid @RequestBody OssRepoUpdateRequest request) {
        return AdminOssRepoResponse.from(ossRepoCategoryService.replaceCategories(repoId, request.categoryCodes()));
    }
}
