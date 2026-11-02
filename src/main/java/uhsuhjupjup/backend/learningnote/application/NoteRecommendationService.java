package uhsuhjupjup.backend.learningnote.application;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import uhsuhjupjup.backend.article.application.KeywordArticleQueryService;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighbor;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.config.NoteAsyncConfig;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysis;
import uhsuhjupjup.backend.learningnote.application.dto.NoteRecommendationResult;
import uhsuhjupjup.backend.learningnote.application.dto.RecommendedArticleResult;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NoteRecommendationService {

    private static final int MAX_RECOMMENDATIONS = 5;
    private static final int MAX_NEIGHBORS = 20;
    private static final double ORIGINAL_WEIGHT = 2.0;
    private static final double RELATED_WEIGHT = 1.0;

    private final NoteService noteService;
    private final NoteAnalyzer noteAnalyzer;
    private final NoteKeywordRepository noteKeywordRepository;
    private final ArticleKeywordRepository articleKeywordRepository;
    private final ArticleRepository articleRepository;
    private final KeywordArticleQueryService keywordArticleQueryService;
    private final Scheduler noteDbScheduler;

    public NoteRecommendationService(NoteService noteService,
                                     NoteAnalyzer noteAnalyzer,
                                     NoteKeywordRepository noteKeywordRepository,
                                     ArticleKeywordRepository articleKeywordRepository,
                                     ArticleRepository articleRepository,
                                     KeywordArticleQueryService keywordArticleQueryService,
                                     @Qualifier(NoteAsyncConfig.NOTE_DB_SCHEDULER) Scheduler noteDbScheduler) {
        this.noteService = noteService;
        this.noteAnalyzer = noteAnalyzer;
        this.noteKeywordRepository = noteKeywordRepository;
        this.articleKeywordRepository = articleKeywordRepository;
        this.articleRepository = articleRepository;
        this.keywordArticleQueryService = keywordArticleQueryService;
        this.noteDbScheduler = noteDbScheduler;
    }

    public Mono<NoteRecommendationResult> recommendAsync(Long noteId, Long memberId) {
        LearningNote note = noteService.get(noteId, memberId);
        if (note.getAnalyzedAt() != null) {
            return Mono.fromCallable(() -> recommendSaved(noteId)).subscribeOn(noteDbScheduler);
        }
        return noteAnalyzer.analyzeAsync(note)
                .publishOn(noteDbScheduler)
                .map(analysis -> analysis.fallback()
                        ? recommendFor(analysis.keywordNames(), analysis.keywordIds())
                        : recommendSaved(noteId));
    }

    public NoteRecommendationResult recommend(Long noteId, Long memberId) {
        LearningNote note = noteService.get(noteId, memberId);
        if (note.getAnalyzedAt() == null) {
            NoteAnalysis analysis = noteAnalyzer.analyze(note);
            if (analysis.fallback()) {
                return recommendFor(analysis.keywordNames(), analysis.keywordIds());
            }
        }
        return recommendSaved(noteId);
    }

    private NoteRecommendationResult recommendSaved(Long noteId) {
        return recommendFor(noteKeywordRepository.findKeywordNamesByNoteId(noteId),
                noteKeywordRepository.findKeywordIdsByNoteId(noteId));
    }

    private NoteRecommendationResult recommendFor(List<String> keywords, List<Long> keywordIds) {
        if (keywordIds.isEmpty()) {
            return new NoteRecommendationResult(keywords, List.of());
        }

        Map<Long, Double> weightByKeyword = expandedKeywordWeights(keywordIds);

        Map<Long, Double> scoreByArticle = new HashMap<>();
        weightByKeyword.forEach((keywordId, weight) -> {
            for (Long articleId : keywordArticleQueryService.candidateArticleIds(keywordId)) {
                scoreByArticle.merge(articleId, weight, Double::sum);
            }
        });
        if (scoreByArticle.isEmpty()) {
            return new NoteRecommendationResult(keywords, List.of());
        }

        Map<Long, Article> articleById = articleRepository.findWithBlogByIdIn(scoreByArticle.keySet()).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        List<Long> articleIds = articleById.values().stream()
                .sorted(Comparator.comparingDouble((Article article) -> scoreByArticle.get(article.getId())).reversed()
                        .thenComparing(Article::getCollectedAt, Comparator.reverseOrder()))
                .limit(MAX_RECOMMENDATIONS)
                .map(Article::getId)
                .toList();

        Map<Long, List<String>> matchedByArticle = matchedKeywordsByArticle(articleIds, weightByKeyword.keySet());

        List<RecommendedArticleResult> articles = articleIds.stream()
                .map(articleById::get)
                .filter(Objects::nonNull)
                .map(article -> new RecommendedArticleResult(
                        article, matchedByArticle.getOrDefault(article.getId(), List.of())))
                .toList();
        return new NoteRecommendationResult(keywords, articles);
    }

    private Map<Long, Double> expandedKeywordWeights(List<Long> noteKeywordIds) {
        Map<Long, Double> weights = new HashMap<>();
        for (Long keywordId : noteKeywordIds) {
            weights.put(keywordId, ORIGINAL_WEIGHT);
        }
        List<KeywordNeighbor> neighbors = articleKeywordRepository.findNeighbors(
                noteKeywordIds, PageRequest.of(0, MAX_NEIGHBORS));
        for (KeywordNeighbor neighbor : neighbors) {
            weights.putIfAbsent(neighbor.keywordId(), RELATED_WEIGHT);
        }
        return weights;
    }

    private Map<Long, List<String>> matchedKeywordsByArticle(List<Long> articleIds, Set<Long> matchKeywordIds) {
        return articleKeywordRepository.findWithKeywordByArticleIdIn(articleIds).stream()
                .filter(articleKeyword -> matchKeywordIds.contains(articleKeyword.getKeyword().getId()))
                .collect(Collectors.groupingBy(
                        articleKeyword -> articleKeyword.getArticle().getId(),
                        Collectors.mapping(articleKeyword -> articleKeyword.getKeyword().getName(), Collectors.toList())));
    }
}
