package uhsuhjupjup.backend.common.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LoginAuthUserArgumentResolverTest {

    @Mock
    private NativeWebRequest webRequest;

    private final LoginAuthUserArgumentResolver resolver = new LoginAuthUserArgumentResolver();

    @Test
    void 토큰이_없으면_401() {
        given(webRequest.getAttribute(FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST))
                .willReturn(null);

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 토큰이_있으면_AuthUser를_그대로_반환() {
        AuthUser authUser = new AuthUser("google", "uid-1", "octocat@github.com");
        given(webRequest.getAttribute(FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST))
                .willReturn(authUser);

        Object resolved = resolver.resolveArgument(null, null, webRequest, null);

        assertThat(resolved).isSameAs(authUser);
    }
}
