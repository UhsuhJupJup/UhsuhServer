package uhsuhjupjup.backend.emailsubscription.application;

import java.time.Duration;
import java.util.Optional;

/**
 * 비회원 구독 관리(매직 링크)용 토큰 저장소. 발급은 TTL을 두고 저장하고,
 * peek는 삭제 없이 검증만 한다 — 만료 전까지 조회·수정에 반복 사용하는 관리 세션 토큰이다.
 */
public interface ManageLinkTokens {

    String issue(Long emailSubscriberId, Duration ttl);

    Optional<Long> peek(String token);
}
