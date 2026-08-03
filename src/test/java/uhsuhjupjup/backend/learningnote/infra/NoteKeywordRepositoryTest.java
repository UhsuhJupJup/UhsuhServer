package uhsuhjupjup.backend.learningnote.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import uhsuhjupjup.backend.config.JpaAuditingConfig;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.domain.NoteKeyword;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class NoteKeywordRepositoryTest extends MySqlTestSupport {

    @Autowired
    private NoteKeywordRepository noteKeywordRepository;
    @Autowired
    private LearningNoteRepository learningNoteRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private MemberRepository memberRepository;

    private LearningNote note;
    private Keyword redis;
    private Keyword caching;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.create("github", "uid-1", "note@example.com"));
        note = learningNoteRepository.save(LearningNote.create(member, "제목", "학습 노트 본문"));
        redis = keywordRepository.save(Keyword.create("Redis"));
        caching = keywordRepository.save(Keyword.create("캐싱"));
    }

    @Test
    void 노트의_키워드_id를_조회한다() {
        noteKeywordRepository.save(NoteKeyword.of(note, redis));
        noteKeywordRepository.save(NoteKeyword.of(note, caching));

        List<Long> ids = noteKeywordRepository.findKeywordIdsByNoteId(note.getId());

        assertThat(ids).containsExactlyInAnyOrder(redis.getId(), caching.getId());
    }

    @Test
    void 여러_노트의_노트_키워드_쌍을_조회한다() {
        noteKeywordRepository.save(NoteKeyword.of(note, redis));
        noteKeywordRepository.save(NoteKeyword.of(note, caching));

        List<NoteKeywordRepository.NoteKeywordId> pairs =
                noteKeywordRepository.findKeywordIdsByNoteIdIn(List.of(note.getId()));

        assertThat(pairs).extracting(
                        NoteKeywordRepository.NoteKeywordId::getNoteId,
                        NoteKeywordRepository.NoteKeywordId::getKeywordId)
                .containsExactlyInAnyOrder(
                        tuple(note.getId(), redis.getId()),
                        tuple(note.getId(), caching.getId()));
    }

    @Test
    void 노트별로_전체_삭제한다() {
        noteKeywordRepository.save(NoteKeyword.of(note, redis));
        noteKeywordRepository.save(NoteKeyword.of(note, caching));

        noteKeywordRepository.deleteByNoteId(note.getId());

        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(note.getId())).isEmpty();
    }
}
