package uhseojupjup.backend.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhseojupjup.backend.common.domain.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_uid", nullable = false, length = 100)
    private String providerUid;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "consent_at")
    private LocalDateTime consentAt;

    @Column(name = "unsubscribe_token", nullable = false, length = 36)
    private String unsubscribeToken;

    private Member(String provider, String providerUid, String email) {
        this.provider = provider;
        this.providerUid = providerUid;
        this.email = email;
        this.unsubscribeToken = UUID.randomUUID().toString();
    }

    public static Member create(String provider, String providerUid, String email) {
        return new Member(provider, providerUid, email);
    }

    public void agreeConsent(LocalDateTime at) {
        if (consentAt == null) {
            this.consentAt = at;
        }
    }

    public boolean hasConsented() {
        return consentAt != null;
    }
}
