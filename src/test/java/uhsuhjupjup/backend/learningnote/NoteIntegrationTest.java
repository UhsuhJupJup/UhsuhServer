package uhsuhjupjup.backend.learningnote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteIntegrationTest extends MySqlTestSupport {

    private static final String BEARER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private LearningNoteRepository learningNoteRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.create("github", "uid-int", "note@example.com"));
        given(firebaseTokenVerifier.verify(anyString()))
                .willReturn(new AuthUser("github", "uid-int", "note@example.com"));
    }

    @Test
    void 노트를_생성하면_목록에_나온다() throws Exception {
        mockMvc.perform(post("/api/notes").header(AUTHORIZATION, BEARER)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Redis 캐싱\",\"content\":\"TTL, eviction 공부\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Redis 캐싱"))
                .andExpect(jsonPath("$.content").value("TTL, eviction 공부"))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(get("/api/notes").header(AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Redis 캐싱"));
    }

    @Test
    void 제목이_비면_400() throws Exception {
        mockMvc.perform(post("/api/notes").header(AUTHORIZATION, BEARER)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"content\":\"본문\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 내용이_비면_400() throws Exception {
        mockMvc.perform(post("/api/notes").header(AUTHORIZATION, BEARER)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증_없이_목록조회는_401() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 노트_단건_조회() throws Exception {
        Long id = learningNoteRepository.save(LearningNote.create(member, "제목", "내 노트")).getId();

        mockMvc.perform(get("/api/notes/" + id).header(AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.content").value("내 노트"));
    }

    @Test
    void 없는_노트_조회는_404() throws Exception {
        mockMvc.perform(get("/api/notes/999999").header(AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));
    }

    @Test
    void 남의_노트는_조회되지_않고_404() throws Exception {
        Long id = learningNoteRepository.save(LearningNote.create(member, "제목", "내 노트")).getId();
        memberRepository.save(Member.create("github", "uid-other", "other@example.com"));
        given(firebaseTokenVerifier.verify(anyString()))
                .willReturn(new AuthUser("github", "uid-other", "other@example.com"));

        mockMvc.perform(get("/api/notes/" + id).header(AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound());
    }

    @Test
    void 노트_수정() throws Exception {
        Long id = learningNoteRepository.save(LearningNote.create(member, "원본 제목", "원본")).getId();

        mockMvc.perform(put("/api/notes/" + id).header(AUTHORIZATION, BEARER)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"수정 제목\",\"content\":\"수정됨\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 제목"))
                .andExpect(jsonPath("$.content").value("수정됨"));
    }

    @Test
    void 노트_삭제_후_조회는_404() throws Exception {
        Long id = learningNoteRepository.save(LearningNote.create(member, "제목", "삭제 대상")).getId();

        mockMvc.perform(delete("/api/notes/" + id).header(AUTHORIZATION, BEARER))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notes/" + id).header(AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound());
    }

    @Test
    void 수정_내용이_비면_400() throws Exception {
        Long id = learningNoteRepository.save(LearningNote.create(member, "제목", "원본")).getId();

        mockMvc.perform(put("/api/notes/" + id).header(AUTHORIZATION, BEARER)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_노트_수정은_404() throws Exception {
        mockMvc.perform(put("/api/notes/999999").header(AUTHORIZATION, BEARER)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 없는_노트_삭제는_404() throws Exception {
        mockMvc.perform(delete("/api/notes/999999").header(AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound());
    }
}
