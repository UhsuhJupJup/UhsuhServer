package uhsuhjupjup.backend.member.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.support.MemberFixture;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberService memberService;

    private final AuthUser authUser = new AuthUser("github", "uid-1", "octocat@github.com");

    @Test
    void find_returnsExistingMemberByEmail() {
        Member existing = MemberFixture.member(1L, "octocat@github.com");
        given(memberRepository.findByEmail("octocat@github.com")).willReturn(Optional.of(existing));

        Optional<Member> found = memberService.find(authUser);

        assertThat(found).containsSame(existing);
    }

    @Test
    void register_savesAndReturnsNewMember() {
        Member saved = MemberFixture.member(1L, "octocat@github.com");
        given(memberRepository.save(any(Member.class))).willReturn(saved);

        Member result = memberService.register(authUser);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("octocat@github.com");
    }

    @Test
    void register_onUniqueConflict_returnsAlreadyExistingMember() {
        Member existing = MemberFixture.member(1L, "octocat@github.com");
        given(memberRepository.save(any(Member.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));
        given(memberRepository.findByEmail("octocat@github.com")).willReturn(Optional.of(existing));

        Member result = memberService.register(authUser);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void consent_setsConsentAt() {
        Member member = MemberFixture.member(1L, "octocat@github.com");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        Member result = memberService.consent(1L);

        assertThat(result.getConsentAt()).isNotNull();
    }

    @Test
    void consent_isIdempotent_keepsFirstTime() {
        LocalDateTime first = LocalDateTime.of(2026, 6, 24, 9, 10);
        Member member = MemberFixture.consentedMember(1L, "octocat@github.com", first);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        memberService.consent(1L);

        assertThat(member.getConsentAt()).isEqualTo(first);
    }

    @Test
    void consent_whenMemberNotFound_throws() {
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.consent(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
