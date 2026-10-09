package uhsuhjupjup.backend.oss.contributor.ui.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OssContributorSettingUpdateRequest(

        @NotNull(message = "필수 값입니다.")
        @Pattern(regexp = "ko|en", message = "ko, en 중 하나여야 합니다.")
        String language,

        @NotNull(message = "필수 값입니다.")
        Boolean emailEnabled,

        @NotNull(message = "필수 값입니다.")
        Boolean pushEnabled
) {
}
