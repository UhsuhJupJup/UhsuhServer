package uhsuhjupjup.backend.keyword.ui.dto;

import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;

import java.util.List;

public record KeywordGraphResponse(
        KeywordGraphCenterResponse center,
        List<RelatedKeywordResponse> neighbors,
        List<KeywordNeighborLinkResponse> neighborLinks) {

    public static KeywordGraphResponse from(KeywordGraphResult result) {
        return new KeywordGraphResponse(
                KeywordGraphCenterResponse.from(result),
                result.neighborhood().neighbors().stream().map(RelatedKeywordResponse::from).toList(),
                result.neighborhood().neighborLinks().stream().map(KeywordNeighborLinkResponse::from).toList()
        );
    }
}
