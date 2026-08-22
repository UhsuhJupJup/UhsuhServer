package uhsuhjupjup.backend.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.emailsubscription.application.EmailSubscriptionClaimService;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final SessionRevoker sessionRevoker;
    private final EmailSubscriptionClaimService emailSubscriptionClaimService;

    public Optional<Member> find(AuthUser authUser) {
        return memberRepository.findByEmail(authUser.email());
    }

    @Transactional
    public Member register(AuthUser authUser) {
        Member member = memberRepository.save(
                Member.create(authUser.provider(), authUser.providerUid(), authUser.email()));
        emailSubscriptionClaimService.claim(member);
        return member;
    }

    @Transactional
    public Member consent(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.agreeConsent(LocalDateTime.now());
        return member;
    }

    @Transactional
    public void revokeSessions(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.revokeSessions(LocalDateTime.now());
        sessionRevoker.revoke(member.getProviderUid());
    }
}
