package uhsuhjupjup.backend.matching.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.matching.domain.KeywordMatcher;
import uhsuhjupjup.backend.matching.domain.MatchCatalog;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private static final int WINDOW_DAYS = 2;

    private final ArticleRepository articleRepository;
    private final KeywordRepository keywordRepository;
    private final KeywordAliasRepository keywordAliasRepository;
    private final KeywordMatcher keywordMatcher;
    private final ArticleKeywordSaver articleKeywordSaver;

    public MatchingResult matchRecent() {
        List<Article> articles = articleRepository.findByCollectedAtGreaterThanEqual(
                LocalDateTime.now().minusDays(WINDOW_DAYS));
        MatchCatalog catalog = MatchCatalog.from(
                keywordRepository.findAll(), keywordAliasRepository.findAll());

        int articlesTagged = 0;
        int tagsCreated = 0;
        for (Article article : articles) {
            List<KeywordMatch> matches = keywordMatcher.match(article.getTitle(), catalog);
            int created = articleKeywordSaver.saveNewTags(article.getId(), matches);
            if (created > 0) {
                articlesTagged++;
            }
            tagsCreated += created;
        }
        MatchingResult result = new MatchingResult(articles.size(), articlesTagged, tagsCreated);
        log.info("매칭 완료 {}", result);
        return result;
    }
}
