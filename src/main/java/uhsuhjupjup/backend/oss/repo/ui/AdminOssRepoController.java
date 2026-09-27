package uhsuhjupjup.backend.oss.repo.ui;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.repo.application.OssRepoRegistrationService;
import uhsuhjupjup.backend.oss.repo.ui.dto.AdminOssRepoResponse;
import uhsuhjupjup.backend.oss.repo.ui.dto.OssRepoRegisterRequest;

@RestController
@RequestMapping("/api/admin/oss/repos")
@RequiredArgsConstructor
public class AdminOssRepoController implements AdminOssRepoControllerApi {

    private final OssRepoRegistrationService ossRepoRegistrationService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminOssRepoResponse register(@AdminMember Member admin,
                                         @Valid @RequestBody OssRepoRegisterRequest request) {
        return AdminOssRepoResponse.from(ossRepoRegistrationService.register(request.fullName()));
    }
}
