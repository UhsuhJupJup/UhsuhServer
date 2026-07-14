package uhsuhjupjup.backend.support;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import uhsuhjupjup.backend.common.auth.LoginMember;
import uhsuhjupjup.backend.member.domain.Member;

public class LoginMemberStubResolver implements HandlerMethodArgumentResolver {

    private final Member member;

    public LoginMemberStubResolver(Member member) {
        this.member = member;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMember.class)
                && parameter.getParameterType().equals(Member.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return member;
    }
}
