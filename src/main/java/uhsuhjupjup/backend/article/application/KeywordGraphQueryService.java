package uhsuhjupjup.backend.article.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborLink;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborhoodResult;
import uhsuhjupjup.backend.article.application.dto.KeywordRelation;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordGraphQueryService {

    private static final int NEIGHBOR_LIMIT = 10;
    private static final int MIN_NEIGHBORS_FOR_LINK = 2;

    private final ArticleKeywordRepository articleKeywordRepository;

    public KeywordNeighborhoodResult findNeighborhood(Long keywordId) {
        long articleCount = articleKeywordRepository.countByKeywordId(keywordId);
        if (articleCount == 0) {
            return new KeywordNeighborhoodResult(0, List.of(), List.of());
        }
        List<KeywordRelation> neighbors = articleKeywordRepository.findRelatedKeywords(
                keywordId, PageRequest.of(0, NEIGHBOR_LIMIT));
        return new KeywordNeighborhoodResult(articleCount, neighbors, findNeighborLinks(keywordId, neighbors));
    }

    private List<KeywordNeighborLink> findNeighborLinks(Long keywordId, List<KeywordRelation> neighbors) {
        if (neighbors.size() < MIN_NEIGHBORS_FOR_LINK) {
            return List.of();
        }
        List<Long> neighborIds = neighbors.stream().map(KeywordRelation::keywordId).toList();
        return articleKeywordRepository.findNeighborLinks(keywordId, neighborIds);
    }
}
