package uhsuhjupjup.backend.oss.contributor.ui;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.LoginMember;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.contributor.application.OssContributorSettingService;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorLanguage;
import uhsuhjupjup.backend.oss.contributor.ui.dto.OssContributorSettingResponse;
import uhsuhjupjup.backend.oss.contributor.ui.dto.OssContributorSettingUpdateRequest;

@RestController
@RequestMapping("/api/oss/me/settings")
@RequiredArgsConstructor
public class OssContributorSettingController implements OssContributorSettingControllerApi {

    private final OssContributorSettingService ossContributorSettingService;

    @Override
    @GetMapping
    public OssContributorSettingResponse mySettings(@LoginMember Member member) {
        return OssContributorSettingResponse.from(ossContributorSettingService.getSetting(member));
    }

    @Override
    @PutMapping
    public OssContributorSettingResponse replace(@LoginMember Member member,
                                                 @Valid @RequestBody OssContributorSettingUpdateRequest request) {
        return OssContributorSettingResponse.from(ossContributorSettingService.replaceSetting(member,
                OssContributorLanguage.fromCode(request.language()), request.emailEnabled(), request.pushEnabled()));
    }
}
