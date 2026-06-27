package uhseojupjup.backend.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhseojupjup.backend.common.auth.AuthUser;
import uhseojupjup.backend.common.exception.BusinessException;
import uhseojupjup.backend.common.exception.ErrorCode;
import uhseojupjup.backend.member.domain.Member;
import uhseojupjup.backend.member.infra.MemberRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public Optional<Member> find(AuthUser authUser) {
        return memberRepository.findByEmail(authUser.email());
    }

    @Transactional
    public Member register(AuthUser authUser) {
        try {
            return memberRepository.save(
                    Member.create(authUser.provider(), authUser.providerUid(), authUser.email()));
        } catch (DataIntegrityViolationException e) {
            return memberRepository.findByEmail(authUser.email())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        }
    }

    @Transactional
    public Member consent(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.agreeConsent(LocalDateTime.now());
        return member;
    }
}
