package uhsuhjupjup.backend.learningnote.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.learningnote.domain.NoteKeyword;

import java.util.Collection;
import java.util.List;

public interface NoteKeywordRepository extends JpaRepository<NoteKeyword, Long> {

    @Query("select nk.keyword.id from NoteKeyword nk where nk.note.id = :noteId")
    List<Long> findKeywordIdsByNoteId(Long noteId);

    @Query("select nk.keyword.name from NoteKeyword nk where nk.note.id = :noteId order by nk.keyword.name")
    List<String> findKeywordNamesByNoteId(Long noteId);

    @Query("select nk.note.id as noteId, nk.keyword.name as name from NoteKeyword nk "
            + "where nk.note.id in :noteIds order by nk.keyword.name")
    List<NoteKeywordName> findKeywordNamesByNoteIdIn(Collection<Long> noteIds);

    @Modifying
    @Query("delete from NoteKeyword nk where nk.note.id = :noteId")
    void deleteByNoteId(Long noteId);

    interface NoteKeywordName {
        Long getNoteId();

        String getName();
    }
}
