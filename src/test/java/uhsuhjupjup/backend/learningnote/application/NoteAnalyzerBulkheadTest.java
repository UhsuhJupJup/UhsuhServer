package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysis;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.member.domain.Member;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatcher;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class NoteAnalyzerBulkheadTest {

    private final KeywordClassifier keywordClassifier = mock(KeywordClassifier.class);
    private final AsyncKeywordClassifier asyncKeywordClassifier = mock(AsyncKeywordClassifier.class);
    private final KeywordMatcher keywordMatcher = mock(KeywordMatcher.class);
    private final NoteKeywordSaver noteKeywordSaver = mock(NoteKeywordSaver.class);
    private final CountDownLatch firstCallStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirstCall = new CountDownLatch(1);
    private final AtomicInteger aiCalls = new AtomicInteger();
    private NoteAnalyzer noteAnalyzer;
    private BulkheadRegistry bulkheadRegistry;

    @BeforeEach
    void setUp() {
        KeywordRepository keywordRepository = mock(KeywordRepository.class);
        KeywordAliasRepository keywordAliasRepository = mock(KeywordAliasRepository.class);
        given(keywordRepository.findAll()).willReturn(List.of());
        given(keywordAliasRepository.findAll()).willReturn(List.of());
        given(keywordMatcher.match(anyString(), any(MatchCatalog.class))).willReturn(List.of());
        given(keywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class))).willAnswer(invocation -> {
            aiCalls.incrementAndGet();
            firstCallStarted.countDown();
            releaseFirstCall.await(10, TimeUnit.SECONDS);
            return List.of(new KeywordMatch(1L, "ai"));
        });
        bulkheadRegistry = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO)
                .build());
        noteAnalyzer = new NoteAnalyzer(keywordRepository, keywordAliasRepository, keywordClassifier,
                asyncKeywordClassifier, keywordMatcher, noteKeywordSaver, bulkheadRegistry, Schedulers.immediate());
    }

    @Test
    void 동시_호출이_허가_수를_넘으면_넘친_요청은_AI를_부르지_않고_문자열로_폴백한다() throws Exception {
        CompletableFuture<NoteAnalysis> first = CompletableFuture.supplyAsync(() -> noteAnalyzer.analyze(note(1L)));
        assertThat(firstCallStarted.await(10, TimeUnit.SECONDS)).isTrue();

        NoteAnalysis overflow = noteAnalyzer.analyze(note(2L));
        releaseFirstCall.countDown();

        assertThat(overflow.fallback()).isTrue();
        assertThat(first.get(10, TimeUnit.SECONDS).fallback()).isFalse();
        assertThat(aiCalls).hasValue(1);
    }

    @Test
    void 호출이_끝나면_허가를_돌려준다() {
        releaseFirstCall.countDown();

        noteAnalyzer.analyze(note(1L));

        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.AI_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(1);
        assertThat(noteAnalyzer.analyze(note(2L)).fallback()).isFalse();
    }

    @Test
    void 비동기_호출도_허가를_넘으면_AI를_부르지_않고_폴백하고_끝나면_허가를_돌려준다() {
        Sinks.One<List<KeywordMatch>> pending = Sinks.one();
        AtomicInteger asyncCalls = new AtomicInteger();
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class))).willAnswer(invocation -> {
            asyncCalls.incrementAndGet();
            return pending.asMono();
        });
        var first = noteAnalyzer.analyzeAsync(note(1L)).toFuture();

        NoteAnalysis overflow = noteAnalyzer.analyzeAsync(note(2L)).block();
        pending.tryEmitValue(List.of(new KeywordMatch(1L, "ai")));

        assertThat(overflow.fallback()).isTrue();
        assertThat(first.join().fallback()).isFalse();
        assertThat(asyncCalls).hasValue(1);
        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.AI_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(1);
    }

    private static LearningNote note(Long id) {
        LearningNote note = LearningNote.create(Member.create("github", "uid", "e@e.com"), "제목", "본문");
        ReflectionTestUtils.setField(note, "id", id);
        return note;
    }
}
