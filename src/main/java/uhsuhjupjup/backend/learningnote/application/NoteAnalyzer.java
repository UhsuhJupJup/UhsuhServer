package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteAnalyzer {

    private final KeywordRepository keywordRepository;
    private final KeywordAliasRepository keywordAliasRepository;
    private final KeywordClassifier keywordClassifier;
    private final NoteKeywordSaver noteKeywordSaver;

    public void analyze(LearningNote note) {
        MatchCatalog catalog = MatchCatalog.from(
                keywordRepository.findAll(), keywordAliasRepository.findAll());
        List<KeywordMatch> matches = keywordClassifier.classify(note.getTitle(), note.getContent(), catalog);
        noteKeywordSaver.replaceKeywords(note.getId(), matches, LocalDateTime.now());
    }
}
