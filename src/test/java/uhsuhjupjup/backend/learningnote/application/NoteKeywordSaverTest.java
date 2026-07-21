package uhsuhjupjup.backend.learningnote.application;

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
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, NoteKeywordSaver.class})
class NoteKeywordSaverTest extends MySqlTestSupport {

    @Autowired
    private NoteKeywordSaver noteKeywordSaver;
    @Autowired
    private NoteKeywordRepository noteKeywordRepository;
    @Autowired
    private LearningNoteRepository learningNoteRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long noteId;
    private Keyword redis;
    private Keyword caching;
    private Keyword jpa;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.create("github", "uid-1", "note@example.com"));
        noteId = learningNoteRepository.save(LearningNote.create(member, "제목", "본문")).getId();
        redis = keywordRepository.save(Keyword.create("Redis"));
        caching = keywordRepository.save(Keyword.create("캐싱"));
        jpa = keywordRepository.save(Keyword.create("JPA"));
    }

    @Test
    void 키워드를_저장하고_분석시각을_남긴다() {
        noteKeywordSaver.replaceKeywords(noteId,
                List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(caching.getId(), "ai")),
                LocalDateTime.now());

        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId))
                .containsExactlyInAnyOrder(redis.getId(), caching.getId());
        assertThat(learningNoteRepository.findById(noteId).orElseThrow().getAnalyzedAt()).isNotNull();
    }

    @Test
    void 재분석하면_겹치는_키워드가_있어도_교체된다() {
        noteKeywordSaver.replaceKeywords(noteId,
                List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(caching.getId(), "ai")),
                LocalDateTime.now());

        noteKeywordSaver.replaceKeywords(noteId,
                List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(jpa.getId(), "ai")),
                LocalDateTime.now());

        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId))
                .containsExactlyInAnyOrder(redis.getId(), jpa.getId());
    }

    @Test
    void 키워드가_없어도_분석시각은_남긴다() {
        noteKeywordSaver.replaceKeywords(noteId, List.of(), LocalDateTime.now());

        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).isEmpty();
        assertThat(learningNoteRepository.findById(noteId).orElseThrow().getAnalyzedAt()).isNotNull();
    }
}
