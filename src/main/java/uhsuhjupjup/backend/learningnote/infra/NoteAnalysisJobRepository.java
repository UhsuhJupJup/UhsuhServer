package uhsuhjupjup.backend.learningnote.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJob;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJobStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoteAnalysisJobRepository extends JpaRepository<NoteAnalysisJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from NoteAnalysisJob j where j.noteId = :noteId")
    Optional<NoteAnalysisJob> findByIdForUpdate(Long noteId);

    List<NoteAnalysisJob> findByNoteIdIn(Collection<Long> noteIds);

    @Query("select j.noteId from NoteAnalysisJob j where j.status = :status and j.leaseUntil <= :now "
            + "order by j.leaseUntil, j.noteId")
    List<Long> findExpiredLeases(NoteAnalysisJobStatus status, LocalDateTime now, Pageable pageable);

    @Query("select j.noteId from NoteAnalysisJob j where j.status = :status and j.nextAttemptAt <= :now "
            + "order by j.nextAttemptAt, j.noteId")
    List<Long> findDueAttempts(NoteAnalysisJobStatus status, LocalDateTime now, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NoteAnalysisJob j set j.status = :running, j.leaseToken = :token, j.leaseUntil = :leaseUntil "
            + "where j.noteId = :noteId and ((j.status = :pending and j.nextAttemptAt <= :now) "
            + "or (j.status = :running and j.leaseUntil <= :now))")
    int claim(Long noteId, String token, LocalDateTime leaseUntil, LocalDateTime now,
              NoteAnalysisJobStatus pending, NoteAnalysisJobStatus running);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NoteAnalysisJob j set j.acknowledgedAt = :now where j.noteId = :noteId "
            + "and j.noteRevision = :revision and j.status = :done and j.acknowledgedAt is null")
    int acknowledge(Long noteId, long revision, LocalDateTime now, NoteAnalysisJobStatus done);
}
