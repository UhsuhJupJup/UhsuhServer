package uhsuhjupjup.backend.keyword.ui.dto;

import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;

public record KeywordGraphCenterResponse(Long id, String name, long articleCount) {

    public static KeywordGraphCenterResponse from(KeywordGraphResult result) {
        return new KeywordGraphCenterResponse(
                result.keyword().getId(),
                result.keyword().getName(),
                result.neighborhood().articleCount());
    }
}
