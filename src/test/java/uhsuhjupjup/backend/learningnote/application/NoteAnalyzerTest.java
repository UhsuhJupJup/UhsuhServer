package uhsuhjupjup.backend.learningnote.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NoteAnalyzerTest {

    @Mock
    private KeywordRepository keywordRepository;
    @Mock
    private KeywordAliasRepository keywordAliasRepository;
    @Mock
    private KeywordClassifier keywordClassifier;
    @Mock
    private NoteKeywordSaver noteKeywordSaver;
    @InjectMocks
    private NoteAnalyzer noteAnalyzer;

    @Test
    void 노트_제목과_본문으로_분류하고_저장에_위임한다() {
        LearningNote note = LearningNote.create(Member.create("github", "uid", "e@e.com"), "Redis 캐싱", "TTL 정리");
        ReflectionTestUtils.setField(note, "id", 1L);
        List<KeywordMatch> matches = List.of(new KeywordMatch(10L, "ai"));
        given(keywordRepository.findAll()).willReturn(List.of());
        given(keywordAliasRepository.findAll()).willReturn(List.of());
        given(keywordClassifier.classify(eq("Redis 캐싱"), eq("TTL 정리"), any(MatchCatalog.class)))
                .willReturn(matches);

        noteAnalyzer.analyze(note);

        verify(keywordClassifier).classify(eq("Redis 캐싱"), eq("TTL 정리"), any(MatchCatalog.class));
        verify(noteKeywordSaver).replaceKeywords(eq(1L), eq(matches), any(LocalDateTime.class));
    }
}
