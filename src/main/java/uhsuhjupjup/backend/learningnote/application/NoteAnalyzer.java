package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import uhsuhjupjup.backend.config.NoteAsyncConfig;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NoteAnalyzer {

    static final String AI_BULKHEAD = "noteKeywordAnalysis";

    private final KeywordRepository keywordRepository;
    private final KeywordAliasRepository keywordAliasRepository;
    private final AsyncKeywordClassifier asyncKeywordClassifier;
    private final NoteKeywordSaver noteKeywordSaver;
    private final Bulkhead aiBulkhead;
    private final Scheduler noteDbScheduler;

    public NoteAnalyzer(KeywordRepository keywordRepository,
                        KeywordAliasRepository keywordAliasRepository,
                        AsyncKeywordClassifier asyncKeywordClassifier,
                        NoteKeywordSaver noteKeywordSaver,
                        BulkheadRegistry bulkheadRegistry,
                        @Qualifier(NoteAsyncConfig.NOTE_DB_SCHEDULER) Scheduler noteDbScheduler) {
        this.keywordRepository = keywordRepository;
        this.keywordAliasRepository = keywordAliasRepository;
        this.asyncKeywordClassifier = asyncKeywordClassifier;
        this.noteKeywordSaver = noteKeywordSaver;
        this.aiBulkhead = bulkheadRegistry.bulkhead(AI_BULKHEAD);
        this.noteDbScheduler = noteDbScheduler;
    }

    public Mono<Boolean> analyzeAsync(LearningNote note) {
        long revision = note.getContentRevision();
        return classifyAsync(note.getTitle(), note.getContent())
                .transformDeferred(BulkheadOperator.of(aiBulkhead))
                .publishOn(noteDbScheduler)
                .map(matches -> noteKeywordSaver.replaceKeywords(note.getId(), revision, matches, LocalDateTime.now()));
    }

    public Mono<List<KeywordMatch>> classifyAsync(String title, String content) {
        MatchCatalog catalog = MatchCatalog.from(keywordRepository.findAll(), keywordAliasRepository.findAll());
        return Mono.defer(() -> asyncKeywordClassifier.classify(title, content, catalog))
                .switchIfEmpty(Mono.error(() -> new KeywordClassificationException("AI 분류 응답이 비었습니다", null)));
    }
}
