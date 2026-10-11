package uhsuhjupjup.backend.article.application.dto;

import java.util.List;

public record KeywordNeighborhoodResult(
        long articleCount,
        List<KeywordRelation> neighbors,
        List<KeywordNeighborLink> neighborLinks) {
}
