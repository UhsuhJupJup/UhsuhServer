package uhsuhjupjup.backend.member.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.ui.dto.LoginRequest;
import uhsuhjupjup.backend.member.ui.dto.MemberResponse;
import uhsuhjupjup.backend.common.auth.LoginMember;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @PostMapping("/login")
    public MemberResponse login(@LoginMember Member member,
                                @RequestBody(required = false) LoginRequest request) {
        Member result = member;
        if (request != null && Boolean.TRUE.equals(request.consent()) && !member.hasConsented()) {
            result = memberService.consent(member.getId());
        }
        return MemberResponse.from(result);
    }
}
