package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.learningnote.application.dto.NoteRecommendationResult;
import uhsuhjupjup.backend.learningnote.application.dto.RecommendedArticleResult;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteRecommendationService {

    private static final int MAX_RECOMMENDATIONS = 5;

    private final NoteService noteService;
    private final NoteAnalyzer noteAnalyzer;
    private final NoteKeywordRepository noteKeywordRepository;
    private final ArticleKeywordRepository articleKeywordRepository;
    private final ArticleRepository articleRepository;

    public NoteRecommendationResult recommend(Long noteId, Long memberId) {
        LearningNote note = noteService.get(noteId, memberId);
        if (note.getAnalyzedAt() == null) {
            noteAnalyzer.analyze(note);
        }
        List<String> keywords = noteKeywordRepository.findKeywordNamesByNoteId(noteId);
        List<Long> keywordIds = noteKeywordRepository.findKeywordIdsByNoteId(noteId);
        if (keywordIds.isEmpty()) {
            return new NoteRecommendationResult(keywords, List.of());
        }
        List<Long> articleIds = articleKeywordRepository.findTopArticleIdsByKeywordIds(
                keywordIds, PageRequest.of(0, MAX_RECOMMENDATIONS));
        if (articleIds.isEmpty()) {
            return new NoteRecommendationResult(keywords, List.of());
        }
        Map<Long, List<String>> matchedByArticle = matchedKeywordsByArticle(articleIds, Set.copyOf(keywordIds));
        Map<Long, Article> articleById = articleRepository.findWithBlogByIdIn(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        List<RecommendedArticleResult> articles = articleIds.stream()
                .map(articleById::get)
                .filter(Objects::nonNull)
                .map(article -> new RecommendedArticleResult(
                        article, matchedByArticle.getOrDefault(article.getId(), List.of())))
                .toList();
        return new NoteRecommendationResult(keywords, articles);
    }

    private Map<Long, List<String>> matchedKeywordsByArticle(List<Long> articleIds, Set<Long> noteKeywordIds) {
        return articleKeywordRepository.findWithKeywordByArticleIdIn(articleIds).stream()
                .filter(articleKeyword -> noteKeywordIds.contains(articleKeyword.getKeyword().getId()))
                .collect(Collectors.groupingBy(
                        articleKeyword -> articleKeyword.getArticle().getId(),
                        Collectors.mapping(articleKeyword -> articleKeyword.getKeyword().getName(), Collectors.toList())));
    }
}
