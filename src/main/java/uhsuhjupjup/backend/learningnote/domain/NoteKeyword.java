package uhsuhjupjup.backend.learningnote.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.keyword.domain.Keyword;

@Entity
@Table(name = "note_keyword")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private LearningNote note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    private NoteKeyword(LearningNote note, Keyword keyword) {
        this.note = note;
        this.keyword = keyword;
    }

    public static NoteKeyword of(LearningNote note, Keyword keyword) {
        return new NoteKeyword(note, keyword);
    }
}
