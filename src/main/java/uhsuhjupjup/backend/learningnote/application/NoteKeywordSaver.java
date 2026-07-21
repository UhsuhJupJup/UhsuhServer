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
import java.util.List;

@Component
@RequiredArgsConstructor
public class NoteKeywordSaver {

    private final NoteKeywordRepository noteKeywordRepository;
    private final LearningNoteRepository learningNoteRepository;
    private final KeywordRepository keywordRepository;

    @Transactional
    public void replaceKeywords(Long noteId, List<KeywordMatch> matches, LocalDateTime analyzedAt) {
        noteKeywordRepository.deleteByNoteId(noteId);
        LearningNote note = learningNoteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalStateException("분석 대상 노트를 찾을 수 없습니다: " + noteId));
        List<NoteKeyword> toSave = matches.stream()
                .map(match -> NoteKeyword.of(note, keywordRepository.getReferenceById(match.keywordId())))
                .toList();
        noteKeywordRepository.saveAll(toSave);
        note.markAnalyzed(analyzedAt);
    }
}
