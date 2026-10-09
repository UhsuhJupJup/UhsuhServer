package uhsuhjupjup.backend.oss.contributor.ui.dto;

import uhsuhjupjup.backend.oss.contributor.application.dto.OssContributorSettingResult;

public record OssContributorSettingResponse(
        String language,
        boolean emailEnabled,
        boolean pushEnabled,
        String notificationEmail) {

    public static OssContributorSettingResponse from(OssContributorSettingResult result) {
        return new OssContributorSettingResponse(result.language().code(), result.emailEnabled(),
                result.pushEnabled(), result.notificationEmail());
    }
}
