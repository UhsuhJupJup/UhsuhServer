package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class NoteAnalyzerBulkheadTest {

    private final AsyncKeywordClassifier asyncKeywordClassifier = mock(AsyncKeywordClassifier.class);
    private final NoteKeywordSaver noteKeywordSaver = mock(NoteKeywordSaver.class);
    private final KeywordRepository keywordRepository = mock(KeywordRepository.class);
    private final KeywordAliasRepository keywordAliasRepository = mock(KeywordAliasRepository.class);
    private NoteAnalyzer noteAnalyzer;
    private BulkheadRegistry bulkheadRegistry;

    @BeforeEach
    void setUp() {
        given(keywordRepository.findAll()).willReturn(List.of());
        given(keywordAliasRepository.findAll()).willReturn(List.of());
        given(noteKeywordSaver.replaceKeywords(anyLong(), anyLong(), anyList(), any(LocalDateTime.class)))
                .willReturn(true);
        bulkheadRegistry = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO)
                .build());
        noteAnalyzer = new NoteAnalyzer(keywordRepository, keywordAliasRepository, asyncKeywordClassifier,
                noteKeywordSaver, bulkheadRegistry, Schedulers.immediate());
    }

    @Test
    void 허가를_넘으면_AI를_부르지_않고_한도_초과를_알리고_끝나면_허가를_돌려준다() {
        Sinks.One<List<KeywordMatch>> pending = Sinks.one();
        AtomicInteger asyncCalls = new AtomicInteger();
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class))).willAnswer(invocation -> {
            asyncCalls.incrementAndGet();
            return pending.asMono();
        });
        var first = noteAnalyzer.analyzeAsync(note(1L)).toFuture();

        assertThatThrownBy(() -> noteAnalyzer.analyzeAsync(note(2L)).block())
                .isInstanceOf(BulkheadFullException.class);
        pending.tryEmitValue(List.of(new KeywordMatch(1L, "ai")));

        assertThat(first.join()).isTrue();
        assertThat(asyncCalls).hasValue(1);
        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(1);
    }

    @Test
    void 호출을_취소하면_허가를_돌려준다() {
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.never());
        var call = noteAnalyzer.analyzeAsync(note(1L)).subscribe();
        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isZero();

        call.dispose();

        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(1);
    }

    @Test
    void 즉시_요청은_설정한_한도까지만_AI를_부르고_나머지는_바로_거절한다() {
        int permits = 15;
        BulkheadRegistry registry = BulkheadRegistry.ofDefaults();
        registry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD, BulkheadConfig.custom()
                .maxConcurrentCalls(permits)
                .maxWaitDuration(Duration.ZERO)
                .build());
        NoteAnalyzer limited = new NoteAnalyzer(keywordRepository, keywordAliasRepository, asyncKeywordClassifier,
                noteKeywordSaver, registry, Schedulers.immediate());
        AtomicInteger asyncCalls = new AtomicInteger();
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class))).willAnswer(invocation -> {
            asyncCalls.incrementAndGet();
            return Mono.never();
        });
        AtomicInteger rejected = new AtomicInteger();
        List<Disposable> calls = new ArrayList<>();

        for (long id = 1; id <= permits + 5; id++) {
            calls.add(limited.analyzeAsync(note(id)).subscribe(ignored -> {
            }, error -> {
                if (error instanceof BulkheadFullException) {
                    rejected.incrementAndGet();
                }
            }));
        }

        assertThat(asyncCalls).hasValue(permits);
        assertThat(rejected).hasValue(5);
        calls.forEach(Disposable::dispose);
        assertThat(registry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(permits);
    }

    @Test
    void AI가_실패해도_허가를_돌려준다() {
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.error(new KeywordClassificationException("모두 실패", new RuntimeException("503"))));

        assertThatThrownBy(() -> noteAnalyzer.analyzeAsync(note(1L)).block())
                .isInstanceOf(KeywordClassificationException.class);

        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(1);
    }

    @Test
    void 결과_저장이_실패해도_허가를_돌려준다() {
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.just(List.of(new KeywordMatch(1L, "ai"))));
        given(noteKeywordSaver.replaceKeywords(anyLong(), anyLong(), anyList(), any(LocalDateTime.class)))
                .willThrow(new IllegalStateException("DB 연결 끊김"));

        assertThatThrownBy(() -> noteAnalyzer.analyzeAsync(note(1L)).block())
                .isInstanceOf(IllegalStateException.class);

        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(1);
    }

    private static LearningNote note(Long id) {
        LearningNote note = LearningNote.create(Member.create("github", "uid", "e@e.com"), "제목", "본문");
        ReflectionTestUtils.setField(note, "id", id);
        return note;
    }
}
