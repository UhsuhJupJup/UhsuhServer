package uhsuhjupjup.backend.article.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordArticleQueryService {

    private static final int CANDIDATE_DAYS = 90;
    private static final int CANDIDATE_SIZE = 100;

    private final ArticleKeywordRepository articleKeywordRepository;

    public List<Long> candidateArticleIds(Long keywordId) {
        return articleKeywordRepository.findCandidateArticleIds(
                keywordId,
                LocalDateTime.now().minusDays(CANDIDATE_DAYS),
                PageRequest.of(0, CANDIDATE_SIZE));
    }
}
