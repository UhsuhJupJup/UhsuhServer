package uhsuhjupjup.backend.member.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.member.application.SessionRevoker;

@Slf4j
@Component
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "false")
class LoggingSessionRevoker implements SessionRevoker {

    @Override
    public void revoke(String providerUid) {
        log.info("[세션폐기-로그] providerUid={} 리프레시 토큰 폐기(로컬 무효화만)", providerUid);
    }
}
