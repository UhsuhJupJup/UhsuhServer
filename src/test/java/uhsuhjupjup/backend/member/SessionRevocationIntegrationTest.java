package uhsuhjupjup.backend.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.domain.Role;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionRevocationIntegrationTest extends MySqlTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @Test
    void afterSelfLogoutAll_staleToken_isRejectedAsSessionRevoked() throws Exception {
        Member member = memberRepository.save(Member.create("google", "uid-1", "user@example.com"));
        Instant loggedInAnHourAgo = Instant.now().minusSeconds(3600);
        given(firebaseTokenVerifier.verify("stale-token"))
                .willReturn(new AuthUser("google", "uid-1", "user@example.com", loggedInAnHourAgo));

        mockMvc.perform(get("/api/members/me").header(AUTHORIZATION, "Bearer stale-token"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/members/me/sessions").header(AUTHORIZATION, "Bearer stale-token"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me").header(AUTHORIZATION, "Bearer stale-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getSessionsValidAfter())
                .isNotNull();
    }

    @Test
    void afterRevocation_freshLoginToken_isAccepted() throws Exception {
        memberRepository.save(Member.create("google", "uid-1", "user@example.com"));
        given(firebaseTokenVerifier.verify("old-token"))
                .willReturn(new AuthUser("google", "uid-1", "user@example.com", Instant.now().minusSeconds(3600)));

        mockMvc.perform(delete("/api/members/me/sessions").header(AUTHORIZATION, "Bearer old-token"))
                .andExpect(status().isNoContent());

        given(firebaseTokenVerifier.verify("fresh-token"))
                .willReturn(new AuthUser("google", "uid-1", "user@example.com", Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/api/members/me").header(AUTHORIZATION, "Bearer fresh-token"))
                .andExpect(status().isOk());
    }

    @Test
    void adminForceLogout_revokesTargetSession() throws Exception {
        Member admin = Member.create("google", "admin-uid", "admin@example.com");
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        memberRepository.save(admin);
        given(firebaseTokenVerifier.verify("admin-token"))
                .willReturn(new AuthUser("google", "admin-uid", "admin@example.com", Instant.now()));

        Member target = memberRepository.save(Member.create("google", "target-uid", "target@example.com"));

        mockMvc.perform(delete("/api/admin/members/" + target.getId() + "/sessions")
                        .header(AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isNoContent());

        given(firebaseTokenVerifier.verify("target-token"))
                .willReturn(new AuthUser("google", "target-uid", "target@example.com", Instant.now().minusSeconds(3600)));

        mockMvc.perform(get("/api/members/me").header(AUTHORIZATION, "Bearer target-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }
}
