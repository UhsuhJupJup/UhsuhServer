package uhsuhjupjup.backend.oss.repo.ui.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OssRepoRegisterRequest(

        @NotNull(message = "필수 값입니다.")
        @Pattern(regexp = OssRepoRegisterRequest.FULL_NAME, message = "GitHub 레포 이름(owner/name) 형식이어야 합니다.")
        String fullName
) {

    private static final String OWNER = "(?=[A-Za-z0-9-]{1,39}/)[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*";
    private static final String NAME = "(?!\\.{1,2}$)[A-Za-z0-9._-]{1,100}";
    private static final String FULL_NAME = OWNER + "/" + NAME;

    public OssRepoRegisterRequest {
        if (fullName != null) {
            fullName = fullName.strip();
        }
    }
}
