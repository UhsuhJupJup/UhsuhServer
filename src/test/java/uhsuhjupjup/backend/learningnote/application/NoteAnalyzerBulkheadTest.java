package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.Duration;
import java.time.LocalDateTime;
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
    private NoteAnalyzer noteAnalyzer;
    private BulkheadRegistry bulkheadRegistry;

    @BeforeEach
    void setUp() {
        KeywordRepository keywordRepository = mock(KeywordRepository.class);
        KeywordAliasRepository keywordAliasRepository = mock(KeywordAliasRepository.class);
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
        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.AI_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(1);
    }

    @Test
    void 호출을_취소하면_허가를_돌려준다() {
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.never());
        var call = noteAnalyzer.analyzeAsync(note(1L)).subscribe();
        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.AI_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isZero();

        call.dispose();

        assertThat(bulkheadRegistry.bulkhead(NoteAnalyzer.AI_BULKHEAD).getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(1);
    }

    private static LearningNote note(Long id) {
        LearningNote note = LearningNote.create(Member.create("github", "uid", "e@e.com"), "제목", "본문");
        ReflectionTestUtils.setField(note, "id", id);
        return note;
    }
}
