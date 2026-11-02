package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysis;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatcher;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NoteAnalyzer {

    static final String AI_BULKHEAD = "noteKeywordAnalysis";

    private final KeywordRepository keywordRepository;
    private final KeywordAliasRepository keywordAliasRepository;
    private final KeywordClassifier keywordClassifier;
    private final KeywordMatcher keywordMatcher;
    private final NoteKeywordSaver noteKeywordSaver;
    private final Bulkhead aiBulkhead;

    public NoteAnalyzer(KeywordRepository keywordRepository,
                        KeywordAliasRepository keywordAliasRepository,
                        KeywordClassifier keywordClassifier,
                        KeywordMatcher keywordMatcher,
                        NoteKeywordSaver noteKeywordSaver,
                        BulkheadRegistry bulkheadRegistry) {
        this.keywordRepository = keywordRepository;
        this.keywordAliasRepository = keywordAliasRepository;
        this.keywordClassifier = keywordClassifier;
        this.keywordMatcher = keywordMatcher;
        this.noteKeywordSaver = noteKeywordSaver;
        this.aiBulkhead = bulkheadRegistry.bulkhead(AI_BULKHEAD);
    }

    public NoteAnalysis analyze(LearningNote note) {
        List<Keyword> keywords = keywordRepository.findAll();
        MatchCatalog catalog = MatchCatalog.from(keywords, keywordAliasRepository.findAll());
        try {
            List<KeywordMatch> matches = aiBulkhead.executeSupplier(
                    () -> keywordClassifier.classify(note.getTitle(), note.getContent(), catalog));
            noteKeywordSaver.replaceKeywords(note.getId(), matches, LocalDateTime.now());
            return NoteAnalysis.saved();
        } catch (BulkheadFullException e) {
            log.warn("노트 AI 동시 호출 한도 초과, 문자열 기반 추출로 폴백 noteId={}", note.getId());
            return fallback(note, keywords, catalog);
        } catch (KeywordClassificationException e) {
            log.warn("노트 AI 분석 실패, 문자열 기반 추출로 폴백 noteId={} 사유={}", note.getId(), e.toString());
            return fallback(note, keywords, catalog);
        }
    }

    private NoteAnalysis fallback(LearningNote note, List<Keyword> keywords, MatchCatalog catalog) {
        Map<Long, String> nameById = keywords.stream()
                .collect(Collectors.toMap(Keyword::getId, Keyword::getName, (first, second) -> first));
        List<Long> keywordIds = keywordMatcher.match(searchText(note), catalog).stream()
                .map(KeywordMatch::keywordId)
                .distinct()
                .toList();
        List<String> keywordNames = keywordIds.stream()
                .map(nameById::get)
                .filter(name -> name != null)
                .toList();
        return NoteAnalysis.fallback(keywordIds, keywordNames);
    }

    private static String searchText(LearningNote note) {
        String content = note.getContent() == null ? "" : note.getContent();
        return note.getTitle() + "\n" + content;
    }
}
