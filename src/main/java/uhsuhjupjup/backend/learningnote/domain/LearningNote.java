package uhsuhjupjup.backend.learningnote.domain;

import jakarta.persistence.Column;
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
import uhsuhjupjup.backend.common.domain.BaseEntity;
import uhsuhjupjup.backend.member.domain.Member;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    private LearningNote(Member member, String title, String content) {
        this.member = member;
        this.title = title;
        this.content = content;
    }

    public static LearningNote create(Member member, String title, String content) {
        return new LearningNote(member, title, content);
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
        this.analyzedAt = null;
    }
}
