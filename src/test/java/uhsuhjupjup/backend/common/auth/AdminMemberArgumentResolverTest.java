package uhsuhjupjup.backend.common.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.domain.Role;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminMemberArgumentResolverTest {

    @Mock
    private MemberService memberService;

    @Mock
    private NativeWebRequest webRequest;

    @InjectMocks
    private AdminMemberArgumentResolver resolver;

    private final AuthUser authUser = new AuthUser("github", "1", "admin@eoseo.dev");

    @Test
    void 토큰이_없으면_401() {
        given(webRequest.getAttribute(FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST))
                .willReturn(null);

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 회원이_없으면_403() {
        given(webRequest.getAttribute(FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST))
                .willReturn(authUser);
        given(memberService.find(authUser)).willReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 관리자가_아니면_403() {
        given(webRequest.getAttribute(FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST))
                .willReturn(authUser);
        given(memberService.find(authUser)).willReturn(Optional.of(member(Role.USER)));

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 관리자면_회원을_반환() {
        Member admin = member(Role.ADMIN);
        given(webRequest.getAttribute(FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST))
                .willReturn(authUser);
        given(memberService.find(authUser)).willReturn(Optional.of(admin));

        Object resolved = resolver.resolveArgument(null, null, webRequest, null);

        assertThat(resolved).isSameAs(admin);
    }

    private Member member(Role role) {
        Member member = Member.create("github", "1", "admin@eoseo.dev");
        ReflectionTestUtils.setField(member, "role", role);
        return member;
    }
}
