package uhseojupjup.backend.member.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhseojupjup.backend.member.domain.Member;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    Optional<Member> findByUnsubscribeToken(String unsubscribeToken);
}
