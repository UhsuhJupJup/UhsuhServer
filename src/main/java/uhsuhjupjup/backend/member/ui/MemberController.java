package uhsuhjupjup.backend.member.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.ui.dto.ConsentResponse;
import uhsuhjupjup.backend.member.ui.dto.MemberResponse;
import uhsuhjupjup.backend.common.auth.LoginMember;

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
