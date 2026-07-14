package uhsuhjupjup.backend.support;

import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.member.domain.Member;

import java.time.LocalDateTime;

public final class MemberFixture {

    private MemberFixture() {
    }

    public static Member member(Long id, String provider, String providerUid, String email) {
        Member member = Member.create(provider, providerUid, email);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    public static Member member(Long id, String email) {
        return member(id, "github", "uid-" + id, email);
    }

    public static Member consentedMember(Long id, String email, LocalDateTime consentAt) {
        Member member = member(id, email);
        ReflectionTestUtils.setField(member, "consentAt", consentAt);
        return member;
    }
}
