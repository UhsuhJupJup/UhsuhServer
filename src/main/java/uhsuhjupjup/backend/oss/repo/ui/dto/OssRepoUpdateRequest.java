package uhsuhjupjup.backend.oss.repo.ui.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OssRepoUpdateRequest(

        @NotNull(message = "필수 값입니다.")
        @Size(max = 2, message = "최대 2개까지 지정할 수 있습니다.")
        List<@NotBlank(message = "빈 코드는 넣을 수 없습니다.") String> categoryCodes
) {

    public OssRepoUpdateRequest {
        if (categoryCodes != null) {
            categoryCodes = categoryCodes.stream().distinct().toList();
        }
    }
}
