package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.article.application.dto.KeywordEdge;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighbor;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.application.dto.GraphEdge;
import uhsuhjupjup.backend.learningnote.application.dto.GraphNode;
import uhsuhjupjup.backend.learningnote.application.dto.NoteGraphResult;
import uhsuhjupjup.backend.learningnote.application.dto.NoteRecommendationResult;
import uhsuhjupjup.backend.learningnote.application.dto.RecommendedArticleResult;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteGraphService {

    private static final int MAX_NEIGHBORS = 10;

    private final NoteService noteService;
    private final NoteRecommendationService noteRecommendationService;
    private final NoteKeywordRepository noteKeywordRepository;
    private final ArticleKeywordRepository articleKeywordRepository;
    private final KeywordRepository keywordRepository;

    public NoteGraphResult graph(Long noteId, Long memberId) {
        LearningNote note = noteService.get(noteId, memberId);
        NoteRecommendationResult recommendation = noteRecommendationService.recommend(noteId, memberId);
        List<Long> noteKeywordIds = noteKeywordRepository.findKeywordIdsByNoteId(noteId);

        Set<Long> graphKeywordIds = new LinkedHashSet<>(noteKeywordIds);
        articleKeywordRepository.findNeighbors(noteKeywordIds, PageRequest.of(0, MAX_NEIGHBORS))
                .forEach(neighbor -> graphKeywordIds.add(neighbor.keywordId()));

        Map<Long, String> nameByKeyword = keywordRepository.findAllById(graphKeywordIds).stream()
                .collect(Collectors.toMap(Keyword::getId, Keyword::getName));

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        String noteNodeId = "note:" + noteId;
        nodes.add(new GraphNode(noteNodeId, "note", note.getTitle(), null, null, null));

        Set<Long> noteKeywordIdSet = Set.copyOf(noteKeywordIds);
        for (Long keywordId : graphKeywordIds) {
            nodes.add(new GraphNode("kw:" + keywordId, "keyword",
                    nameByKeyword.getOrDefault(keywordId, ""), noteKeywordIdSet.contains(keywordId), null, null));
        }
        for (Long keywordId : noteKeywordIds) {
            edges.add(new GraphEdge(noteNodeId, "kw:" + keywordId, null));
        }
        if (graphKeywordIds.size() >= 2) {
            for (KeywordEdge edge : articleKeywordRepository.findCooccurrenceEdges(graphKeywordIds)) {
                edges.add(new GraphEdge("kw:" + edge.keywordAId(), "kw:" + edge.keywordBId(), edge.cooccurrence()));
            }
        }

        List<Long> articleIds = recommendation.articles().stream()
                .map(article -> article.article().getId())
                .toList();
        int rank = 1;
        for (RecommendedArticleResult recommended : recommendation.articles()) {
            nodes.add(new GraphNode("art:" + recommended.article().getId(), "article",
                    recommended.article().getTitle(), null, rank++, null));
        }
        if (!articleIds.isEmpty()) {
            articleKeywordRepository.findWithKeywordByArticleIdIn(articleIds).stream()
                    .filter(articleKeyword -> graphKeywordIds.contains(articleKeyword.getKeyword().getId()))
                    .forEach(articleKeyword -> edges.add(new GraphEdge(
                            "art:" + articleKeyword.getArticle().getId(),
                            "kw:" + articleKeyword.getKeyword().getId(), null)));
        }

        return new NoteGraphResult(nodes, edges);
    }
}
