package uhsuhjupjup.backend.pipeline.matching.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.article.application.KeywordArticleQueryService;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ArticleKeywordSaver {

    private final ArticleKeywordRepository articleKeywordRepository;
    private final ArticleRepository articleRepository;
    private final KeywordRepository keywordRepository;
    private final KeywordArticleQueryService keywordArticleQueryService;

    @Transactional
    public int recordClassification(Long articleId, List<KeywordMatch> matches, LocalDateTime classifiedAt) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalStateException("분류 대상 글을 찾을 수 없습니다: " + articleId));
        int created = saveNewTags(article, matches);
        article.markClassified(classifiedAt);
        return created;
    }

    private int saveNewTags(Article article, List<KeywordMatch> matches) {
        if (matches.isEmpty()) {
            return 0;
        }
        Set<Long> existing = Set.copyOf(articleKeywordRepository.findKeywordIdsByArticleId(article.getId()));
        List<ArticleKeyword> toSave = matches.stream()
                .filter(match -> !existing.contains(match.keywordId()))
                .map(match -> ArticleKeyword.of(
                        article,
                        keywordRepository.getReferenceById(match.keywordId()),
                        match.matchedVia()))
                .toList();
        articleKeywordRepository.saveAll(toSave);
        toSave.forEach(ak -> keywordArticleQueryService.evict(ak.getKeyword().getId()));
        return toSave.size();
    }
}
