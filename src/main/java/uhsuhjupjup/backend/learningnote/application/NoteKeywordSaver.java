package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.domain.NoteKeyword;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NoteKeywordSaver {

    private final NoteKeywordRepository noteKeywordRepository;
    private final LearningNoteRepository learningNoteRepository;
    private final KeywordRepository keywordRepository;

    @Transactional
    public boolean replaceKeywords(Long noteId, long expectedRevision, List<KeywordMatch> matches,
                                   LocalDateTime analyzedAt) {
        LearningNote note = learningNoteRepository.findByIdForUpdate(noteId).orElse(null);
        if (note == null || !note.isRevision(expectedRevision)) {
            return false;
        }
        Set<Long> wanted = matches.stream()
                .map(KeywordMatch::keywordId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> kept = new HashSet<>();
        List<Long> stale = new ArrayList<>();
        for (NoteKeywordRepository.NoteKeywordRow row : noteKeywordRepository.findRowsByNoteId(noteId)) {
            if (wanted.contains(row.getKeywordId())) {
                kept.add(row.getKeywordId());
            } else {
                stale.add(row.getId());
            }
        }
        if (!stale.isEmpty()) {
            noteKeywordRepository.deleteAllByIdInBatch(stale);
        }
        List<NoteKeyword> added = wanted.stream()
                .filter(keywordId -> !kept.contains(keywordId))
                .map(keywordId -> NoteKeyword.of(note, keywordRepository.getReferenceById(keywordId)))
                .toList();
        noteKeywordRepository.saveAll(added);
        note.markAnalyzed(analyzedAt);
        return true;
    }
}
