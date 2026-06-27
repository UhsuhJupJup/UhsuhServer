package uhsuhjupjup.backend.member.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.support.LoginMemberStubResolver;
import uhsuhjupjup.backend.support.MemberFixture;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
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
    private Member loginMember;

    @BeforeEach
    void setUp() {
        loginMember = MemberFixture.member(1L, "octocat@github.com");
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(memberService))
                .setCustomArgumentResolvers(new LoginMemberStubResolver(loginMember))
                .build();
    }

    @Test
    void login_withoutBody_returnsMember() throws Exception {
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("octocat@github.com"))
                .andExpect(jsonPath("$.consentAt").doesNotExist());

        verify(memberService, never()).consent(eq(1L));
    }

    @Test
    void login_withConsent_recordsConsent() throws Exception {
        Member consented = MemberFixture.consentedMember(1L, "octocat@github.com",
                LocalDateTime.of(2026, 6, 25, 12, 0));
        given(memberService.consent(1L)).willReturn(consented);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentAt").exists());

        verify(memberService).consent(1L);
    }
}
