package uhsuhjupjup.backend.learningnote.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;

import java.util.List;
import java.util.Optional;

public interface LearningNoteRepository extends JpaRepository<LearningNote, Long> {

    List<LearningNote> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<LearningNote> findByIdAndMemberId(Long id, Long memberId);
}
