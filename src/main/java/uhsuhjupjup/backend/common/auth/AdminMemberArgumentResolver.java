package uhsuhjupjup.backend.common.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;

@Component
@RequiredArgsConstructor
public class AdminMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private final MemberService memberService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AdminMember.class)
                && parameter.getParameterType().equals(Member.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        AuthUser authUser = (AuthUser) webRequest.getAttribute(
                FirebaseAuthInterceptor.AUTH_USER, RequestAttributes.SCOPE_REQUEST);
        if (authUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Member member = memberService.find(authUser)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (!member.isSessionValid(authUser.authTime())) {
            throw new BusinessException(ErrorCode.SESSION_REVOKED);
        }
        if (!member.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return member;
    }
}
