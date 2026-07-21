package uhsuhjupjup.backend.learningnote.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import uhsuhjupjup.backend.config.JpaAuditingConfig;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class LearningNoteRepositoryTest extends MySqlTestSupport {

    @Autowired
    private LearningNoteRepository learningNoteRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TestEntityManager em;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.create("github", "uid-1", "note@example.com"));
    }

    @Test
    void 저장하면_제목_본문_감사시각이_채워지고_analyzedAt은_null이다() {
        LearningNote saved = learningNoteRepository.save(LearningNote.create(member, "제목", "학습 노트 본문"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("제목");
        assertThat(saved.getContent()).isEqualTo("학습 노트 본문");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getAnalyzedAt()).isNull();
    }

    @Test
    void 회원별_최신순으로_조회한다() {
        LearningNote older = learningNoteRepository.save(LearningNote.create(member, "예전", "예전 노트"));
        learningNoteRepository.save(LearningNote.create(member, "최근", "최근 노트"));
        forceCreatedAt(older.getId(), LocalDateTime.now().minusDays(1));

        List<LearningNote> notes = learningNoteRepository.findByMemberIdOrderByCreatedAtDesc(member.getId());

        assertThat(notes).extracting(LearningNote::getContent)
                .containsExactly("최근 노트", "예전 노트");
    }

    @Test
    void 다른_회원의_노트는_조회되지_않는다() {
        Member other = memberRepository.save(Member.create("github", "uid-2", "other@example.com"));
        learningNoteRepository.save(LearningNote.create(member, "내 제목", "내 노트"));
        learningNoteRepository.save(LearningNote.create(other, "남 제목", "남의 노트"));

        List<LearningNote> notes = learningNoteRepository.findByMemberIdOrderByCreatedAtDesc(member.getId());

        assertThat(notes).extracting(LearningNote::getContent).containsExactly("내 노트");
    }

    private void forceCreatedAt(Long id, LocalDateTime at) {
        em.getEntityManager()
                .createNativeQuery("update learning_note set created_at = :at where id = :id")
                .setParameter("at", at)
                .setParameter("id", id)
                .executeUpdate();
        em.clear();
    }
}
