package uhsuhjupjup.backend.learningnote.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.article.application.KeywordArticleQueryService;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.learningnote.application.dto.NoteRecommendationResult;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NoteRecommendationServiceTest {

    @Mock
    private NoteService noteService;
    @Mock
    private NoteAnalyzer noteAnalyzer;
    @Mock
    private NoteKeywordRepository noteKeywordRepository;
    @Mock
    private KeywordArticleQueryService keywordArticleQueryService;
    @Mock
    private ArticleKeywordRepository articleKeywordRepository;
    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private NoteRecommendationService noteRecommendationService;

    // 목을 '먼저' 만들어 스터빙을 끝낸 뒤 반환한다 (given(...).willReturn(헬퍼()) 중첩 금지)
    private Article article(long id, LocalDateTime collectedAt) {
        Article article = mock(Article.class);
        lenient().when(article.getId()).thenReturn(id);
        lenient().when(article.getCollectedAt()).thenReturn(collectedAt);
        return article;
    }

    private LearningNote analyzedNote() {
        LearningNote note = mock(LearningNote.class);
        lenient().when(note.getAnalyzedAt()).thenReturn(LocalDateTime.now());
        return note;
    }

    @Test
    void 겹침이_많은_글이_위로_온다() {
        LearningNote note = analyzedNote();
        Article article1 = article(1L, LocalDateTime.now().minusDays(1));
        Article article2 = article(2L, LocalDateTime.now().minusDays(2));

        given(noteService.get(1L, 1L)).willReturn(note);
        given(noteKeywordRepository.findKeywordNamesByNoteId(1L)).willReturn(List.of("자바", "스프링"));
        given(noteKeywordRepository.findKeywordIdsByNoteId(1L)).willReturn(List.of(100L, 200L));
        // 자바 → [1,2], 스프링 → [2]  ⇒ 글2는 겹침 2, 글1은 겹침 1
        given(keywordArticleQueryService.candidateArticleIds(100L)).willReturn(List.of(1L, 2L));
        given(keywordArticleQueryService.candidateArticleIds(200L)).willReturn(List.of(2L));
        given(articleRepository.findWithBlogByIdIn(anyCollection())).willReturn(List.of(article1, article2));
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(anyCollection())).willReturn(List.of());

        NoteRecommendationResult result = noteRecommendationService.recommend(1L, 1L);

        assertThat(result.articles())
                .extracting(recommended -> recommended.article().getId())
                .containsExactly(2L, 1L);
    }

    @Test
    void 겹침이_같으면_최신순으로_정렬된다() {
        LearningNote note = analyzedNote();
        Article recent = article(1L, LocalDateTime.now().minusDays(1));
        Article older = article(2L, LocalDateTime.now().minusDays(5));

        given(noteService.get(1L, 1L)).willReturn(note);
        given(noteKeywordRepository.findKeywordNamesByNoteId(1L)).willReturn(List.of("자바"));
        given(noteKeywordRepository.findKeywordIdsByNoteId(1L)).willReturn(List.of(100L));
        given(keywordArticleQueryService.candidateArticleIds(100L)).willReturn(List.of(1L, 2L)); // 둘 다 겹침 1
        given(articleRepository.findWithBlogByIdIn(anyCollection())).willReturn(List.of(recent, older));
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(anyCollection())).willReturn(List.of());

        NoteRecommendationResult result = noteRecommendationService.recommend(1L, 1L);

        assertThat(result.articles())
                .extracting(recommended -> recommended.article().getId())
                .containsExactly(1L, 2L); // 최신(글1)이 먼저
    }

    @Test
    void 최대_5개까지만_추천한다() {
        LearningNote note = analyzedNote();
        List<Article> six = List.of(
                article(1L, LocalDateTime.now().minusDays(1)),
                article(2L, LocalDateTime.now().minusDays(2)),
                article(3L, LocalDateTime.now().minusDays(3)),
                article(4L, LocalDateTime.now().minusDays(4)),
                article(5L, LocalDateTime.now().minusDays(5)),
                article(6L, LocalDateTime.now().minusDays(6)));

        given(noteService.get(1L, 1L)).willReturn(note);
        given(noteKeywordRepository.findKeywordNamesByNoteId(1L)).willReturn(List.of("자바"));
        given(noteKeywordRepository.findKeywordIdsByNoteId(1L)).willReturn(List.of(100L));
        given(keywordArticleQueryService.candidateArticleIds(100L)).willReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L));
        given(articleRepository.findWithBlogByIdIn(anyCollection())).willReturn(six);
        given(articleKeywordRepository.findWithKeywordByArticleIdIn(anyCollection())).willReturn(List.of());

        NoteRecommendationResult result = noteRecommendationService.recommend(1L, 1L);

        assertThat(result.articles())
                .extracting(recommended -> recommended.article().getId())
                .containsExactly(1L, 2L, 3L, 4L, 5L); // 최신 5개
    }

    @Test
    void 키워드가_없으면_빈_추천을_반환하고_캐시를_보지_않는다() {
        LearningNote note = analyzedNote();

        given(noteService.get(1L, 1L)).willReturn(note);
        given(noteKeywordRepository.findKeywordNamesByNoteId(1L)).willReturn(List.of());
        given(noteKeywordRepository.findKeywordIdsByNoteId(1L)).willReturn(List.of());

        NoteRecommendationResult result = noteRecommendationService.recommend(1L, 1L);

        assertThat(result.articles()).isEmpty();
        verifyNoInteractions(keywordArticleQueryService);
    }

    @Test
    void 후보_글이_없으면_상세조회_없이_빈_추천을_반환한다() {
        LearningNote note = analyzedNote();

        given(noteService.get(1L, 1L)).willReturn(note);
        given(noteKeywordRepository.findKeywordNamesByNoteId(1L)).willReturn(List.of("자바"));
        given(noteKeywordRepository.findKeywordIdsByNoteId(1L)).willReturn(List.of(100L));
        given(keywordArticleQueryService.candidateArticleIds(100L)).willReturn(List.of());

        NoteRecommendationResult result = noteRecommendationService.recommend(1L, 1L);

        assertThat(result.articles()).isEmpty();
        verifyNoInteractions(articleRepository);
    }

    @Test
    void 아직_분석되지_않은_노트는_분석을_먼저_수행한다() {
        LearningNote coldNote = mock(LearningNote.class);
        given(coldNote.getAnalyzedAt()).willReturn(null);
        given(noteService.get(1L, 1L)).willReturn(coldNote);
        given(noteKeywordRepository.findKeywordNamesByNoteId(1L)).willReturn(List.of());
        given(noteKeywordRepository.findKeywordIdsByNoteId(1L)).willReturn(List.of());

        noteRecommendationService.recommend(1L, 1L);

        verify(noteAnalyzer).analyze(coldNote);
    }
}
