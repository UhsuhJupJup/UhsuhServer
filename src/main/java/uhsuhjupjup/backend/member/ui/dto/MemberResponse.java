package uhsuhjupjup.backend.member.ui.dto;

import uhsuhjupjup.backend.member.domain.Member;

import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        String email,
        String provider,
        LocalDateTime consentAt,
        LocalDateTime createdAt
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getProvider(),
                member.getConsentAt(),
                member.getCreatedAt()
        );
    }
}
