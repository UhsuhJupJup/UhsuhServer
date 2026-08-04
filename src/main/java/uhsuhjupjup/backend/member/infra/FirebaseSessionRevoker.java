package uhsuhjupjup.backend.member.infra;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.member.application.SessionRevoker;

@Slf4j
@Component
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
class FirebaseSessionRevoker implements SessionRevoker {

    private final FirebaseAuth firebaseAuth;

    @Override
    public void revoke(String providerUid) {
        try {
            firebaseAuth.revokeRefreshTokens(providerUid);
        } catch (FirebaseAuthException e) {
            log.warn("리프레시 토큰 폐기 실패 providerUid={}", providerUid, e);
        }
    }
}
