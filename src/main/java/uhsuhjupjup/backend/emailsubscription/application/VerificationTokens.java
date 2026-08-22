package uhsuhjupjup.backend.emailsubscription.application;

import java.time.Duration;
import java.util.Optional;

/**
 * 이메일 확인용 일회성 토큰 저장소. 발급(issue)은 TTL을 두고 저장하고,
 * 소비(consume)는 원자적으로 조회 후 삭제한다(재사용 불가).
 */
public interface VerificationTokens {

    String issue(Long emailSubscriberId, Duration ttl);

    Optional<Long> consume(String token);
}
