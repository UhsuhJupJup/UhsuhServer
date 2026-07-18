package uhsuhjupjup.backend.common.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LoginMemberArgumentResolverTest {

    @Mock
    private MemberService memberService;

    @Mock
    private NativeWebRequest webRequest;

    @InjectMocks
    private LoginMemberArgumentResolver resolver;

    private final AuthUser authUser = new AuthUser("google", "uid-1", "octocat@github.com");

    @Test
    void 토큰이_없으면_401() {
        given(webRequest.getAttribute(FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST))
                .willReturn(null);

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 기존_회원이면_그대로_반환() {
        Member existing = Member.create("google", "uid-1", "octocat@github.com");
        given(webRequest.getAttribute(FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST))
                .willReturn(authUser);
        given(memberService.find(authUser)).willReturn(Optional.of(existing));

        Object resolved = resolver.resolveArgument(null, null, webRequest, null);

        assertThat(resolved).isSameAs(existing);
    }

    @Test
    void 회원이_없으면_401() {
        given(webRequest.getAttribute(FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST))
                .willReturn(authUser);
        given(memberService.find(authUser)).willReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
