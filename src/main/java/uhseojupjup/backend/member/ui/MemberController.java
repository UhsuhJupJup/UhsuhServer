package uhseojupjup.backend.member.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhseojupjup.backend.member.application.MemberService;
import uhseojupjup.backend.member.domain.Member;
import uhseojupjup.backend.member.ui.dto.ConsentResponse;
import uhseojupjup.backend.member.ui.dto.MemberResponse;
import uhseojupjup.backend.common.auth.LoginMember;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public MemberResponse me(@LoginMember Member member) {
        return MemberResponse.from(member);
    }

    @PostMapping("/me/consent")
    public ConsentResponse consent(@LoginMember Member member) {
        Member updated = memberService.consent(member.getId());
        return new ConsentResponse(updated.getConsentAt());
    }
}
