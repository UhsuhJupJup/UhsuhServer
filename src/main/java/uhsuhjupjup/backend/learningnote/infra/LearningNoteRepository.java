package uhsuhjupjup.backend.learningnote.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;

import java.util.List;
import java.util.Optional;

public interface LearningNoteRepository extends JpaRepository<LearningNote, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from LearningNote n where n.id = :id")
    Optional<LearningNote> findByIdForUpdate(Long id);

    List<LearningNote> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<LearningNote> findByIdAndMemberId(Long id, Long memberId);
}
