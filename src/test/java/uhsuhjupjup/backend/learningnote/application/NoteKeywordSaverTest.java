package uhsuhjupjup.backend.learningnote.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
@Import(NoteKeywordSaver.class)
class NoteKeywordSaverTest {

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
        noteKeywordSaver.replaceKeywords(noteId, 0L,
                List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(caching.getId(), "ai")),
                LocalDateTime.now());

        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId))
                .containsExactlyInAnyOrder(redis.getId(), caching.getId());
        assertThat(learningNoteRepository.findById(noteId).orElseThrow().getAnalyzedAt()).isNotNull();
    }

    @Test
    void 재분석하면_겹치는_키워드가_있어도_교체된다() {
        noteKeywordSaver.replaceKeywords(noteId, 0L,
                List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(caching.getId(), "ai")),
                LocalDateTime.now());

        noteKeywordSaver.replaceKeywords(noteId, 0L,
                List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(jpa.getId(), "ai")),
                LocalDateTime.now());

        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId))
                .containsExactlyInAnyOrder(redis.getId(), jpa.getId());
    }

    @Test
    void 재분석에서_그대로인_키워드_행은_지우지_않고_빠진_키워드만_지운다() {
        noteKeywordSaver.replaceKeywords(noteId, 0L,
                List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(caching.getId(), "ai")),
                LocalDateTime.now());
        Long redisRowId = rowIdOf(redis.getId());

        noteKeywordSaver.replaceKeywords(noteId, 0L,
                List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(jpa.getId(), "ai")),
                LocalDateTime.now());

        assertThat(rowIdOf(redis.getId())).isEqualTo(redisRowId);
        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId))
                .containsExactlyInAnyOrder(redis.getId(), jpa.getId());
    }

    @Test
    void 같은_키워드가_여러_번_와도_한_번만_저장한다() {
        noteKeywordSaver.replaceKeywords(noteId, 0L,
                List.of(new KeywordMatch(redis.getId(), "ai"), new KeywordMatch(redis.getId(), "alias")),
                LocalDateTime.now());

        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).containsExactly(redis.getId());
    }

    @Test
    void 다시_분석한_결과가_비면_기존_키워드를_모두_지운다() {
        noteKeywordSaver.replaceKeywords(noteId, 0L,
                List.of(new KeywordMatch(redis.getId(), "ai")), LocalDateTime.now());

        noteKeywordSaver.replaceKeywords(noteId, 0L, List.of(), LocalDateTime.now());

        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).isEmpty();
    }

    @Test
    void 키워드가_없어도_분석시각은_남긴다() {
        noteKeywordSaver.replaceKeywords(noteId, 0L, List.of(), LocalDateTime.now());

        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).isEmpty();
        assertThat(learningNoteRepository.findById(noteId).orElseThrow().getAnalyzedAt()).isNotNull();
    }

    @Test
    void 분석을_시작한_뒤_노트가_수정됐으면_저장하지_않는다() {
        LearningNote note = learningNoteRepository.findById(noteId).orElseThrow();
        note.update("새 제목", "새 본문");
        learningNoteRepository.saveAndFlush(note);

        boolean saved = noteKeywordSaver.replaceKeywords(noteId, 0L,
                List.of(new KeywordMatch(redis.getId(), "ai")), LocalDateTime.now());

        assertThat(saved).isFalse();
        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).isEmpty();
        assertThat(learningNoteRepository.findById(noteId).orElseThrow().getAnalyzedAt()).isNull();
    }

    private Long rowIdOf(Long keywordId) {
        return noteKeywordRepository.findRowsByNoteId(noteId).stream()
                .filter(row -> row.getKeywordId().equals(keywordId))
                .map(NoteKeywordRepository.NoteKeywordRow::getId)
                .findFirst()
                .orElseThrow();
    }
}
