package uhsuhjupjup.backend.common.auth;

import java.time.Instant;

public record AuthUser(String provider, String providerUid, String email, Instant authTime) {

    public AuthUser(String provider, String providerUid, String email) {
        this(provider, providerUid, email, null);
    }
}
