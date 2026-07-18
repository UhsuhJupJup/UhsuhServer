package uhsuhjupjup.backend.member.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.support.LoginAuthUserStubResolver;
import uhsuhjupjup.backend.support.MemberFixture;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private MemberService memberService;

    private MockMvc mockMvc;
    private final AuthUser authUser = new AuthUser("github", "uid-1", "octocat@github.com");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(memberService))
                .setCustomArgumentResolvers(new LoginAuthUserStubResolver(authUser))
                .build();
    }

    @Test
    void login_existingMember_returnsMember() throws Exception {
        Member existing = MemberFixture.member(1L, "octocat@github.com");
        given(memberService.find(authUser)).willReturn(Optional.of(existing));

        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("octocat@github.com"))
                .andExpect(jsonPath("$.consentAt").doesNotExist());

        verify(memberService, never()).register(authUser);
        verify(memberService, never()).consent(anyLong());
    }

    @Test
    void login_newMember_registers() throws Exception {
        Member created = MemberFixture.member(2L, "octocat@github.com");
        given(memberService.find(authUser)).willReturn(Optional.empty());
        given(memberService.register(authUser)).willReturn(created);

        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));

        verify(memberService).register(authUser);
    }

    @Test
    void login_concurrentRegisterConflict_recoversWinner() throws Exception {
        Member winner = MemberFixture.member(3L, "octocat@github.com");
        given(memberService.find(authUser)).willReturn(Optional.empty(), Optional.of(winner));
        given(memberService.register(authUser)).willThrow(new DataIntegrityViolationException("duplicate"));

        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void login_withConsent_recordsConsent() throws Exception {
        Member existing = MemberFixture.member(1L, "octocat@github.com");
        Member consented = MemberFixture.consentedMember(1L, "octocat@github.com",
                LocalDateTime.of(2026, 6, 25, 12, 0));
        given(memberService.find(authUser)).willReturn(Optional.of(existing));
        given(memberService.consent(1L)).willReturn(consented);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentAt").exists());

        verify(memberService).consent(1L);
    }
}
