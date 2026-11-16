package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.learningnote.application.dto.NoteResult;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJob;
import uhsuhjupjup.backend.learningnote.infra.NoteAnalysisJobRepository;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository.NoteKeywordName;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteService {

    private final LearningNoteRepository learningNoteRepository;
    private final NoteKeywordRepository noteKeywordRepository;
    private final NoteAnalysisJobRepository noteAnalysisJobRepository;

    @Transactional
    public LearningNote create(Member member, String title, String content) {
        return learningNoteRepository.save(LearningNote.create(member, title, content));
    }

    public List<NoteResult> findSummaries(Long memberId) {
        List<LearningNote> notes = learningNoteRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        List<Long> analyzedIds = notes.stream()
                .filter(note -> note.getAnalyzedAt() != null)
                .map(LearningNote::getId)
                .toList();
        Map<Long, List<String>> keywordsByNote = analyzedIds.isEmpty() ? Map.of()
                : noteKeywordRepository.findKeywordNamesByNoteIdIn(analyzedIds).stream()
                        .collect(Collectors.groupingBy(
                                NoteKeywordName::getNoteId,
                                Collectors.mapping(NoteKeywordName::getName, Collectors.toList())));
        Map<Long, NoteAnalysisJob> jobsByNote = notes.isEmpty() ? Map.of()
                : noteAnalysisJobRepository.findByNoteIdIn(notes.stream().map(LearningNote::getId).toList()).stream()
                        .collect(Collectors.toMap(NoteAnalysisJob::getNoteId, Function.identity()));
        return notes.stream()
                .map(note -> NoteResult.of(note, keywordsByNote.getOrDefault(note.getId(), List.of()),
                        jobsByNote.get(note.getId())))
                .toList();
    }

    public NoteResult getDetail(Long id, Long memberId) {
        LearningNote note = findOwned(id, memberId);
        List<String> keywords = note.getAnalyzedAt() == null ? List.of()
                : noteKeywordRepository.findKeywordNamesByNoteId(note.getId());
        return NoteResult.of(note, keywords, noteAnalysisJobRepository.findById(note.getId()).orElse(null));
    }

    public LearningNote get(Long id, Long memberId) {
        return findOwned(id, memberId);
    }

    @Transactional
    public LearningNote update(Long id, Long memberId, String title, String content) {
        LearningNote note = learningNoteRepository.findByIdForUpdate(id)
                .filter(found -> found.getMember().getId().equals(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTE_NOT_FOUND));
        note.update(title, content);
        return note;
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        learningNoteRepository.delete(findOwned(id, memberId));
    }

    private LearningNote findOwned(Long id, Long memberId) {
        return learningNoteRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTE_NOT_FOUND));
    }
}
