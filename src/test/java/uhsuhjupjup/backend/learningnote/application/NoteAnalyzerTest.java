package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NoteAnalyzerTest {

    @Mock
    private KeywordRepository keywordRepository;
    @Mock
    private KeywordAliasRepository keywordAliasRepository;
    @Mock
    private AsyncKeywordClassifier asyncKeywordClassifier;
    @Mock
    private NoteKeywordSaver noteKeywordSaver;
    private NoteAnalyzer noteAnalyzer;

    private LearningNote note;

    @BeforeEach
    void setUp() {
        noteAnalyzer = new NoteAnalyzer(keywordRepository, keywordAliasRepository, asyncKeywordClassifier,
                noteKeywordSaver, BulkheadRegistry.ofDefaults(), Schedulers.immediate());
        note = LearningNote.create(Member.create("github", "uid", "e@e.com"), "Redis 캐싱", "TTL 정리");
        ReflectionTestUtils.setField(note, "id", 1L);
        given(keywordRepository.findAll()).willReturn(List.of());
        given(keywordAliasRepository.findAll()).willReturn(List.of());
    }

    @Test
    void 노트_제목과_본문으로_분류하고_분석을_시작한_버전으로_저장한다() {
        List<KeywordMatch> matches = List.of(new KeywordMatch(10L, "ai"));
        given(asyncKeywordClassifier.classify(eq("Redis 캐싱"), eq("TTL 정리"), any(MatchCatalog.class)))
                .willReturn(Mono.just(matches));
        given(noteKeywordSaver.replaceKeywords(eq(1L), eq(0L), eq(matches), any(LocalDateTime.class))).willReturn(true);

        Boolean saved = noteAnalyzer.analyzeAsync(note).block();

        assertThat(saved).isTrue();
    }

    @Test
    void 분류가_모두_실패하면_문자열로_대신하지_않고_실패를_그대로_돌려준다() {
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.error(new KeywordClassificationException("모두 실패", new RuntimeException())));

        assertThatThrownBy(() -> noteAnalyzer.analyzeAsync(note).block())
                .isInstanceOf(KeywordClassificationException.class);
        verifyNoInteractions(noteKeywordSaver);
    }

    @Test
    void 분류_응답이_비면_실패로_돌려준다() {
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.empty());

        assertThatThrownBy(() -> noteAnalyzer.analyzeAsync(note).block())
                .isInstanceOf(KeywordClassificationException.class);
        verifyNoInteractions(noteKeywordSaver);
    }
}
