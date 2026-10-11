package uhsuhjupjup.backend.keyword.application.dto;

import uhsuhjupjup.backend.article.application.dto.KeywordNeighborhoodResult;
import uhsuhjupjup.backend.keyword.domain.Keyword;

public record KeywordGraphResult(Keyword keyword, KeywordNeighborhoodResult neighborhood) {
}
