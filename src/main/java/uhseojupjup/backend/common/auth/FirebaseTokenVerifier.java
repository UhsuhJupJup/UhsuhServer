package uhseojupjup.backend.common.auth;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uhseojupjup.backend.common.exception.BusinessException;
import uhseojupjup.backend.common.exception.ErrorCode;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class FirebaseTokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public AuthUser verify(String idToken) {
        FirebaseToken token;
        try {
            token = firebaseAuth.verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EXPIRED_ID_TOKEN) {
                throw new BusinessException(ErrorCode.EXPIRED_ID_TOKEN);
            }
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN);
        }

        String email = token.getEmail();
        if (!StringUtils.hasText(email) || !token.isEmailVerified()) {
            // 발송 주소(=로그인 이메일)가 검증되지 않으면 회원으로 받을 수 없다.
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        return new AuthUser(extractProvider(token), token.getUid(), email);
    }

    /** firebase.sign_in_provider("google.com"/"github.com") → "google"/"github". */
    private String extractProvider(FirebaseToken token) {
        Object firebase = token.getClaims().get("firebase");
        if (firebase instanceof Map<?, ?> claims) {
            Object signInProvider = claims.get("sign_in_provider");
            if (signInProvider != null) {
                String provider = signInProvider.toString();
                return provider.endsWith(".com")
                        ? provider.substring(0, provider.length() - ".com".length())
                        : provider;
            }
        }
        return "unknown";
    }
}
