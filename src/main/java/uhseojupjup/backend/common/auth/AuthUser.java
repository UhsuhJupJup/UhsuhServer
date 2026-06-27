package uhseojupjup.backend.common.auth;

public record AuthUser(String provider, String providerUid, String email) {
}
