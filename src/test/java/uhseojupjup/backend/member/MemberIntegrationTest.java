package uhseojupjup.backend.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uhseojupjup.backend.common.auth.AuthUser;
import uhseojupjup.backend.common.auth.FirebaseTokenVerifier;
import uhseojupjup.backend.common.exception.BusinessException;
import uhseojupjup.backend.common.exception.ErrorCode;
import uhseojupjup.backend.member.infra.MemberRepository;
import uhseojupjup.backend.support.MySqlTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberIntegrationTest extends MySqlTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @Test
    void login_createsMember_andRecordsConsent() throws Exception {
        given(firebaseTokenVerifier.verify("valid-token"))
                .willReturn(new AuthUser("github", "uid-1", "octocat@github.com"));

        mockMvc.perform(post("/api/auth/login")
                        .header(AUTHORIZATION, "Bearer valid-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("octocat@github.com"))
                .andExpect(jsonPath("$.provider").value("github"))
                .andExpect(jsonPath("$.consentAt").exists());

        assertThat(memberRepository.findByEmail("octocat@github.com")).isPresent();
    }

    @Test
    void me_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_withInvalidToken_returnsInvalidIdToken() throws Exception {
        given(firebaseTokenVerifier.verify("bad-token"))
                .willThrow(new BusinessException(ErrorCode.INVALID_ID_TOKEN));

        mockMvc.perform(get("/api/members/me").header(AUTHORIZATION, "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ID_TOKEN"));
    }
}
