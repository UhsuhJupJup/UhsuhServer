package uhsuhjupjup.backend.learningnote.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import uhsuhjupjup.backend.article.application.dto.KeywordEdge;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighbor;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.application.dto.GraphNode;
import uhsuhjupjup.backend.learningnote.application.dto.NoteGraphResult;
import uhsuhjupjup.backend.learningnote.application.dto.NoteRecommendationResult;
import uhsuhjupjup.backend.learningnote.application.dto.RecommendedArticleResult;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class NoteGraphServiceTest {

    @Mock
    private NoteService noteService;
    @Mock
    private NoteRecommendationService noteRecommendationService;
    @Mock
    private NoteKeywordRepository noteKeywordRepository;
    @Mock
    private ArticleKeywordRepository articleKeywordRepository;
    @Mock
    private KeywordRepository keywordRepository;

    @InjectMocks
    private NoteGraphService noteGraphService;

    private Keyword keyword(long id, String name) {
        Keyword keyword = mock(Keyword.class);
        lenient().when(keyword.getId()).thenReturn(id);
        lenient().when(keyword.getName()).thenReturn(name);
        return keyword;
    }

    @Test
    void 그래프는_노트키워드_이웃_추천글_노드와_간선을_담는다() {
        LearningNote note = mock(LearningNote.class);
        lenient().when(note.getTitle()).thenReturn("MySQL 데드락 회고");
        Article article = mock(Article.class);
        lenient().when(article.getId()).thenReturn(10L);
        lenient().when(article.getTitle()).thenReturn("MySQL 데드락 디버깅");
        Keyword mysql = keyword(100L, "MySQL");
        Keyword transaction = keyword(200L, "트랜잭션");
        ArticleKeyword articleKeyword = mock(ArticleKeyword.class);
        lenient().when(articleKeyword.getArticle()).thenReturn(article);
        lenient().when(articleKeyword.getKeyword()).thenReturn(mysql);

        given(noteService.get(1L, 1L)).willReturn(note);
        given(noteRecommendationService.recommend(1L, 1L)).willReturn(
                new NoteRecommendationResult(List.of("MySQL"),
                        List.of(new RecommendedArticleResult(article, List.of("MySQL")))));
        given(noteKeywordRepository.findKeywordIdsByNoteId(1L)).willReturn(List.of(100L));
        given(articleKeywordRepository.findNeighbors(anyCollection(), any(Pageable.class)))
                .willReturn(List.of(new KeywordNeighbor(200L, 3L)));
        given(keywordRepository.findAllById(any())).willReturn(List.of(mysql, transaction));
        given(articleKeywordRepository.findCooccurrenceEdges(anyCollection()))
                .willReturn(List.of(new KeywordEdge(100L, 200L, 3L)));
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(anyCollection()))
                .willReturn(List.of(articleKeyword));

        NoteGraphResult result = noteGraphService.graph(1L, 1L);

        assertThat(result.nodes()).extracting(GraphNode::id)
                .contains("note:1", "kw:100", "kw:200", "art:10");
        assertThat(result.nodes()).filteredOn(node -> node.id().equals("kw:100"))
                .extracting(GraphNode::inNote).containsExactly(true);
        assertThat(result.nodes()).filteredOn(node -> node.id().equals("kw:200"))
                .extracting(GraphNode::inNote).containsExactly(false);
        assertThat(result.nodes()).filteredOn(node -> node.id().equals("art:10"))
                .extracting(GraphNode::rank).containsExactly(1);
        assertThat(result.edges()).extracting(edge -> edge.source() + ">" + edge.target())
                .contains("note:1>kw:100", "kw:100>kw:200", "art:10>kw:100");
    }
}
