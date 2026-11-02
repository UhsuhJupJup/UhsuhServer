package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysis;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.member.domain.Member;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatcher;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NoteAnalyzerTest {

    @Mock
    private KeywordRepository keywordRepository;
    @Mock
    private KeywordAliasRepository keywordAliasRepository;
    @Mock
    private KeywordClassifier keywordClassifier;
    @Mock
    private AsyncKeywordClassifier asyncKeywordClassifier;
    @Mock
    private KeywordMatcher keywordMatcher;
    @Mock
    private NoteKeywordSaver noteKeywordSaver;
    private NoteAnalyzer noteAnalyzer;

    private LearningNote note;

    @BeforeEach
    void setUp() {
        noteAnalyzer = new NoteAnalyzer(keywordRepository, keywordAliasRepository, keywordClassifier,
                asyncKeywordClassifier, keywordMatcher, noteKeywordSaver, BulkheadRegistry.ofDefaults(),
                Schedulers.immediate());
        note = LearningNote.create(Member.create("github", "uid", "e@e.com"), "Redis 캐싱", "TTL 정리");
        ReflectionTestUtils.setField(note, "id", 1L);
        given(keywordAliasRepository.findAll()).willReturn(List.of());
    }

    @Test
    void 노트_제목과_본문으로_분류하고_저장에_위임한다() {
        List<KeywordMatch> matches = List.of(new KeywordMatch(10L, "ai"));
        given(keywordRepository.findAll()).willReturn(List.of());
        given(keywordClassifier.classify(eq("Redis 캐싱"), eq("TTL 정리"), any(MatchCatalog.class)))
                .willReturn(matches);

        NoteAnalysis analysis = noteAnalyzer.analyze(note);

        assertThat(analysis.fallback()).isFalse();
        verify(noteKeywordSaver).replaceKeywords(eq(1L), eq(matches), any(LocalDateTime.class));
        verifyNoInteractions(keywordMatcher);
    }

    @Test
    void AI_분류가_모두_실패하면_제목과_본문에서_문자열로_뽑고_저장하지_않는다() {
        Keyword redis = keyword(10L, "Redis");
        Keyword ttl = keyword(20L, "TTL");
        given(keywordRepository.findAll()).willReturn(List.of(redis, ttl));
        given(keywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willThrow(new KeywordClassificationException("Claude, GPT 키워드 분류 모두 실패", new RuntimeException()));
        given(keywordMatcher.match(eq("Redis 캐싱\nTTL 정리"), any(MatchCatalog.class)))
                .willReturn(List.of(new KeywordMatch(10L, "title"), new KeywordMatch(20L, "title"),
                        new KeywordMatch(10L, "alias")));

        NoteAnalysis analysis = noteAnalyzer.analyze(note);

        assertThat(analysis.fallback()).isTrue();
        assertThat(analysis.keywordIds()).containsExactly(10L, 20L);
        assertThat(analysis.keywordNames()).containsExactly("Redis", "TTL");
        verifyNoInteractions(noteKeywordSaver);
    }

    @Test
    void 비동기로_분류하고_저장에_위임한다() {
        List<KeywordMatch> matches = List.of(new KeywordMatch(10L, "ai"));
        given(keywordRepository.findAll()).willReturn(List.of());
        given(asyncKeywordClassifier.classify(eq("Redis 캐싱"), eq("TTL 정리"), any(MatchCatalog.class)))
                .willReturn(Mono.just(matches));

        NoteAnalysis analysis = noteAnalyzer.analyzeAsync(note).block();

        assertThat(analysis.fallback()).isFalse();
        verify(noteKeywordSaver).replaceKeywords(eq(1L), eq(matches), any(LocalDateTime.class));
    }

    @Test
    void 비동기_분류가_모두_실패하면_문자열로_폴백하고_저장하지_않는다() {
        Keyword redis = keyword(10L, "Redis");
        given(keywordRepository.findAll()).willReturn(List.of(redis));
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.error(new KeywordClassificationException("모두 실패", new RuntimeException())));
        given(keywordMatcher.match(anyString(), any(MatchCatalog.class)))
                .willReturn(List.of(new KeywordMatch(10L, "title")));

        NoteAnalysis analysis = noteAnalyzer.analyzeAsync(note).block();

        assertThat(analysis.fallback()).isTrue();
        assertThat(analysis.keywordNames()).containsExactly("Redis");
        verifyNoInteractions(noteKeywordSaver);
    }

    private static Keyword keyword(Long id, String name) {
        Keyword keyword = mock(Keyword.class);
        given(keyword.getId()).willReturn(id);
        given(keyword.getName()).willReturn(name);
        return keyword;
    }
}
