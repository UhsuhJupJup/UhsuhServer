package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteService {

    private final LearningNoteRepository learningNoteRepository;

    @Transactional
    public LearningNote create(Member member, String title, String content) {
        return learningNoteRepository.save(LearningNote.create(member, title, content));
    }

    public List<LearningNote> findByMember(Long memberId) {
        return learningNoteRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    public LearningNote get(Long id, Long memberId) {
        return findOwned(id, memberId);
    }

    @Transactional
    public LearningNote update(Long id, Long memberId, String title, String content) {
        LearningNote note = findOwned(id, memberId);
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
