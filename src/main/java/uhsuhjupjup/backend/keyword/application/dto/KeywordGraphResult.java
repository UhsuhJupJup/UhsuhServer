package uhsuhjupjup.backend.keyword.application.dto;

import uhsuhjupjup.backend.article.application.dto.KeywordNeighborhoodResult;
import uhsuhjupjup.backend.keyword.domain.Keyword;

public record KeywordGraphResult(Long keywordId, String keywordName, KeywordNeighborhoodResult neighborhood) {

    public static KeywordGraphResult of(Keyword keyword, KeywordNeighborhoodResult neighborhood) {
        return new KeywordGraphResult(keyword.getId(), keyword.getName(), neighborhood);
    }
}
