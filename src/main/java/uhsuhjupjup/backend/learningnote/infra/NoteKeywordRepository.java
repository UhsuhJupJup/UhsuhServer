package uhsuhjupjup.backend.learningnote.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.learningnote.domain.NoteKeyword;

import java.util.List;

public interface NoteKeywordRepository extends JpaRepository<NoteKeyword, Long> {

    @Query("select nk.keyword.id from NoteKeyword nk where nk.note.id = :noteId")
    List<Long> findKeywordIdsByNoteId(Long noteId);

    void deleteByNoteId(Long noteId);
}
