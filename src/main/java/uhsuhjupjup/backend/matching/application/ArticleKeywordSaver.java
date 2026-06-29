package uhsuhjupjup.backend.matching.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.matching.domain.KeywordMatch;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ArticleKeywordSaver {

    private final ArticleKeywordRepository articleKeywordRepository;
    private final ArticleRepository articleRepository;
    private final KeywordRepository keywordRepository;

    @Transactional
    public int saveNewTags(Long articleId, List<KeywordMatch> matches) {
        if (matches.isEmpty()) {
            return 0;
        }
        Set<Long> existing = Set.copyOf(articleKeywordRepository.findKeywordIdsByArticleId(articleId));
        List<ArticleKeyword> toSave = matches.stream()
                .filter(match -> !existing.contains(match.keywordId()))
                .map(match -> ArticleKeyword.of(
                        articleRepository.getReferenceById(articleId),
                        keywordRepository.getReferenceById(match.keywordId()),
                        match.matchedVia()))
                .toList();
        articleKeywordRepository.saveAll(toSave);
        return toSave.size();
    }
}
