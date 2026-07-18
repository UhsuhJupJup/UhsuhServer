package uhsuhjupjup.backend.member.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.LoginAuthUser;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.ui.dto.LoginRequest;
import uhsuhjupjup.backend.member.ui.dto.MemberResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @PostMapping("/login")
    public MemberResponse login(@LoginAuthUser AuthUser authUser,
                                @RequestBody(required = false) LoginRequest request) {
        Member member = findOrRegister(authUser);
        Member result = member;
        if (request != null && Boolean.TRUE.equals(request.consent()) && !member.hasConsented()) {
            result = memberService.consent(member.getId());
        }
        return MemberResponse.from(result);
    }

    private Member findOrRegister(AuthUser authUser) {
        try {
            return memberService.find(authUser)
                    .orElseGet(() -> memberService.register(authUser));
        } catch (DataIntegrityViolationException e) {
            return memberService.find(authUser)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        }
    }
}
