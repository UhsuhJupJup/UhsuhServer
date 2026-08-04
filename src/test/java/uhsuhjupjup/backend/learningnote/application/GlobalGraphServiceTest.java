package uhsuhjupjup.backend.learningnote.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GlobalGraphServiceTest {

    @Mock
    private GlobalKeywordGraphProvider globalKeywordGraphProvider;
    @Mock
    private LearningNoteRepository noteRepository;
    @Mock
    private NoteKeywordRepository noteKeywordRepository;
    @Mock
    private KeywordRepository keywordRepository;
    @Mock
    private ArticleKeywordRepository articleKeywordRepository;

    @InjectMocks
    private GlobalGraphService globalGraphService;

    private NoteGraphResult globalGraph() {
        return new NoteGraphResult(
                List.of(
                        new GraphNode("kw:1", "keyword", "MySQL", false, null, 5L),
                        new GraphNode("kw:2", "keyword", "Redis", false, null, 3L)),
                List.of(new GraphEdge("kw:1", "kw:2", 4L)));
    }

    @Test
    void 분석된_노트가_없으면_전역_그래프만_반환한다() {
        given(globalKeywordGraphProvider.globalKeywordGraph()).willReturn(globalGraph());
        given(noteRepository.findByMemberIdOrderByCreatedAtDesc(1L)).willReturn(List.of());

        NoteGraphResult result = globalGraphService.graph(1L);

        assertThat(result.nodes()).extracting(GraphNode::id).containsExactly("kw:1", "kw:2");
        assertThat(result.nodes()).extracting(GraphNode::inNote).containsExactly(false, false);
        assertThat(result.edges()).extracting(GraphEdge::source, GraphEdge::target)
                .containsExactly(tuple("kw:1", "kw:2"));
    }

    @Test
    void 분석전_노트는_오버레이에서_제외한다() {
        given(globalKeywordGraphProvider.globalKeywordGraph()).willReturn(globalGraph());
        LearningNote pending = mock(LearningNote.class);
        given(pending.getAnalyzedAt()).willReturn(null);
        given(noteRepository.findByMemberIdOrderByCreatedAtDesc(1L)).willReturn(List.of(pending));

        NoteGraphResult result = globalGraphService.graph(1L);

        assertThat(result.nodes()).extracting(GraphNode::type).containsOnly("keyword");
    }

    @Test
    void 노트_키워드는_mine표시하고_전역밖_키워드와_노트노드_간선을_추가한다() {
        given(globalKeywordGraphProvider.globalKeywordGraph()).willReturn(globalGraph());

        LearningNote note = mock(LearningNote.class);
        given(note.getId()).willReturn(100L);
        given(note.getAnalyzedAt()).willReturn(LocalDateTime.of(2026, 8, 3, 0, 0));
        given(note.getTitle()).willReturn("내 노트");
        given(noteRepository.findByMemberIdOrderByCreatedAtDesc(1L)).willReturn(List.of(note));

        given(noteKeywordRepository.findKeywordIdsByNoteIdIn(any()))
                .willReturn(List.of(noteKeywordId(100L, 1L), noteKeywordId(100L, 9L)));

        Keyword kafka = mock(Keyword.class);
        given(kafka.getId()).willReturn(9L);
        given(kafka.getName()).willReturn("Kafka");
        given(keywordRepository.findAllById(any())).willReturn(List.of(kafka));
        given(articleKeywordRepository.findKeywordFrequenciesByIds(any()))
                .willReturn(List.of(new KeywordFrequency(9L, 2L)));

        NoteGraphResult result = globalGraphService.graph(1L);

        assertThat(result.nodes()).filteredOn(node -> node.id().equals("kw:1"))
                .extracting(GraphNode::inNote, GraphNode::weight).containsExactly(tuple(true, 5L));
        assertThat(result.nodes()).filteredOn(node -> node.id().equals("kw:2"))
                .extracting(GraphNode::inNote).containsExactly(false);
        assertThat(result.nodes()).filteredOn(node -> node.id().equals("kw:9"))
                .extracting(GraphNode::type, GraphNode::label, GraphNode::inNote, GraphNode::weight)
                .containsExactly(tuple("keyword", "Kafka", true, 2L));
        assertThat(result.nodes()).filteredOn(node -> node.id().equals("note:100"))
                .extracting(GraphNode::type, GraphNode::label).containsExactly(tuple("note", "내 노트"));
        assertThat(result.edges()).extracting(GraphEdge::source, GraphEdge::target)
                .contains(tuple("kw:1", "kw:2"), tuple("note:100", "kw:1"), tuple("note:100", "kw:9"));
    }

    private NoteKeywordId noteKeywordId(Long noteId, Long keywordId) {
        return new NoteKeywordId() {
            @Override
            public Long getNoteId() {
                return noteId;
            }

            @Override
            public Long getKeywordId() {
                return keywordId;
            }
        };
    }
}
