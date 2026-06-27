package uhseojupjup.backend.member.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhseojupjup.backend.member.application.MemberService;
import uhseojupjup.backend.member.domain.Member;
import uhseojupjup.backend.support.LoginMemberStubResolver;
import uhseojupjup.backend.support.MemberFixture;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberService memberService;

    private MockMvc mockMvc;
    private Member loginMember;

    @BeforeEach
    void setUp() {
        loginMember = MemberFixture.member(1L, "octocat@github.com");
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(memberService))
                .setCustomArgumentResolvers(new LoginMemberStubResolver(loginMember))
                .build();
    }

    @Test
    void me_returnsCurrentMember() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("octocat@github.com"))
                .andExpect(jsonPath("$.provider").value("github"));
    }

    @Test
    void consent_returnsConsentAt() throws Exception {
        Member consented = MemberFixture.consentedMember(1L, "octocat@github.com",
                LocalDateTime.of(2026, 6, 25, 12, 0));
        given(memberService.consent(1L)).willReturn(consented);

        mockMvc.perform(post("/api/members/me/consent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentAt").exists());
    }
}
