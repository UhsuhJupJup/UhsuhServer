package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.article.application.dto.KeywordFrequency;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.application.dto.GraphEdge;
import uhsuhjupjup.backend.learningnote.application.dto.GraphNode;
import uhsuhjupjup.backend.learningnote.application.dto.NoteGraphResult;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository.NoteKeywordId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalGraphService {

    private final GlobalKeywordGraphProvider globalKeywordGraphProvider;
    private final LearningNoteRepository noteRepository;
    private final NoteKeywordRepository noteKeywordRepository;
    private final KeywordRepository keywordRepository;
    private final ArticleKeywordRepository articleKeywordRepository;

    public NoteGraphResult globalGraph() {
        return globalKeywordGraphProvider.globalKeywordGraph();
    }

    public NoteGraphResult graph(Long memberId) {
        NoteGraphResult global = globalKeywordGraphProvider.globalKeywordGraph();

        List<LearningNote> notes = noteRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .filter(note -> note.getAnalyzedAt() != null)
                .toList();
        if (notes.isEmpty()) {
            return global;
        }

        List<Long> noteIds = notes.stream().map(LearningNote::getId).toList();
        Map<Long, List<Long>> keywordIdsByNote = noteKeywordRepository.findKeywordIdsByNoteIdIn(noteIds).stream()
                .collect(Collectors.groupingBy(NoteKeywordId::getNoteId,
                        Collectors.mapping(NoteKeywordId::getKeywordId, Collectors.toList())));
        Set<Long> myKeywordIds = keywordIdsByNote.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        List<GraphNode> nodes = new ArrayList<>();
        Set<Long> globalKeywordIds = new HashSet<>();
        for (GraphNode node : global.nodes()) {
            if ("keyword".equals(node.type())) {
                Long keywordId = keywordIdOf(node.id());
                globalKeywordIds.add(keywordId);
                nodes.add(new GraphNode(node.id(), node.type(), node.label(),
                        myKeywordIds.contains(keywordId), node.rank(), node.weight()));
            } else {
                nodes.add(node);
            }
        }

        Set<Long> missingKeywordIds = myKeywordIds.stream()
                .filter(keywordId -> !globalKeywordIds.contains(keywordId))
                .collect(Collectors.toSet());
        if (!missingKeywordIds.isEmpty()) {
            Map<Long, String> nameByKeyword = keywordRepository.findAllById(missingKeywordIds).stream()
                    .collect(Collectors.toMap(Keyword::getId, Keyword::getName));
            Map<Long, Long> articleCountByKeyword = articleKeywordRepository.findKeywordFrequenciesByIds(missingKeywordIds).stream()
                    .collect(Collectors.toMap(KeywordFrequency::keywordId, KeywordFrequency::articleCount));
            for (Long keywordId : missingKeywordIds) {
                nodes.add(new GraphNode("kw:" + keywordId, "keyword", nameByKeyword.getOrDefault(keywordId, ""),
                        true, null, articleCountByKeyword.getOrDefault(keywordId, 0L)));
            }
        }

        List<GraphEdge> edges = new ArrayList<>(global.edges());
        for (LearningNote note : notes) {
            String noteNodeId = "note:" + note.getId();
            nodes.add(new GraphNode(noteNodeId, "note", note.getTitle(), null, null, null));
            for (Long keywordId : keywordIdsByNote.getOrDefault(note.getId(), List.of())) {
                edges.add(new GraphEdge(noteNodeId, "kw:" + keywordId, null));
            }
        }

        return new NoteGraphResult(nodes, edges);
    }

    private Long keywordIdOf(String nodeId) {
        return Long.valueOf(nodeId.substring("kw:".length()));
    }
}
