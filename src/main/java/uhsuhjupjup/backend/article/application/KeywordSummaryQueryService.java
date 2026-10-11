package uhsuhjupjup.backend.article.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.article.application.dto.KeywordSummaryResult;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordSummaryQueryService {

    private static final int RELATED_KEYWORD_LIMIT = 8;

    private final ArticleKeywordRepository articleKeywordRepository;

    public KeywordSummaryResult summarize(Long keywordId) {
        long articleCount = articleKeywordRepository.countByKeywordId(keywordId);
        if (articleCount == 0) {
            return new KeywordSummaryResult(0, List.of());
        }
        return new KeywordSummaryResult(
                articleCount,
                articleKeywordRepository.findRelatedKeywords(keywordId, PageRequest.of(0, RELATED_KEYWORD_LIMIT)));
    }
}
