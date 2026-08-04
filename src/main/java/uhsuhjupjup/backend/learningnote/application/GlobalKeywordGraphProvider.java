package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.article.application.dto.KeywordEdge;
import uhsuhjupjup.backend.article.application.dto.KeywordFrequency;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.application.dto.GraphEdge;
import uhsuhjupjup.backend.learningnote.application.dto.GraphNode;
import uhsuhjupjup.backend.learningnote.application.dto.NoteGraphResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class GlobalKeywordGraphProvider {

    private static final int TOP_EDGES = 500;

    private final ArticleKeywordRepository articleKeywordRepository;
    private final KeywordRepository keywordRepository;

    public NoteGraphResult globalKeywordGraph() {
        List<KeywordEdge> topEdges = articleKeywordRepository.findTopCooccurrenceEdges(PageRequest.of(0, TOP_EDGES));
        LinkedHashSet<Long> keywordIds = topEdges.stream()
                .flatMap(edge -> Stream.of(edge.keywordAId(), edge.keywordBId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, Long> articleCountByKeyword = articleKeywordRepository.findKeywordFrequencies().stream()
                .collect(Collectors.toMap(KeywordFrequency::keywordId, KeywordFrequency::articleCount));
        Map<Long, String> nameByKeyword = keywordRepository.findAllById(keywordIds).stream()
                .collect(Collectors.toMap(Keyword::getId, Keyword::getName));

        List<GraphNode> nodes = keywordIds.stream()
                .map(keywordId -> new GraphNode("kw:" + keywordId, "keyword", nameByKeyword.getOrDefault(keywordId, ""),
                        false, null, articleCountByKeyword.getOrDefault(keywordId, 0L)))
                .toList();
        List<GraphEdge> edges = topEdges.stream()
                .map(edge -> new GraphEdge("kw:" + edge.keywordAId(), "kw:" + edge.keywordBId(), edge.cooccurrence()))
                .toList();
        return new NoteGraphResult(nodes, edges);
    }
}
