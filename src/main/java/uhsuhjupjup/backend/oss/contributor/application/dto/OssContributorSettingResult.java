package uhsuhjupjup.backend.oss.contributor.application.dto;

import uhsuhjupjup.backend.oss.contributor.domain.OssContributorLanguage;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorSetting;

public record OssContributorSettingResult(
        OssContributorLanguage language,
        boolean emailEnabled,
        boolean pushEnabled,
        String notificationEmail) {

    public static OssContributorSettingResult of(OssContributorSetting setting, String notificationEmail) {
        return new OssContributorSettingResult(setting.getLanguage(), setting.isEmailEnabled(),
                setting.isPushEnabled(), notificationEmail);
    }
}
