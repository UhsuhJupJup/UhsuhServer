package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class NoteAnalyzer {

    private final KeywordRepository keywordRepository;
    private final KeywordAliasRepository keywordAliasRepository;
    private final KeywordClassifier keywordClassifier;
    private final KeywordMatcher keywordMatcher;
    private final NoteKeywordSaver noteKeywordSaver;

    public NoteAnalysis analyze(LearningNote note) {
        List<Keyword> keywords = keywordRepository.findAll();
        MatchCatalog catalog = MatchCatalog.from(keywords, keywordAliasRepository.findAll());
        try {
            List<KeywordMatch> matches = keywordClassifier.classify(note.getTitle(), note.getContent(), catalog);
            noteKeywordSaver.replaceKeywords(note.getId(), matches, LocalDateTime.now());
            return NoteAnalysis.saved();
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
