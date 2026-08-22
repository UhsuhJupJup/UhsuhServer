package uhsuhjupjup.backend.emailsubscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.common.domain.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_subscriber")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailSubscriber extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "unsubscribe_token", nullable = false, length = 36)
    private String unsubscribeToken;

    private EmailSubscriber(String email) {
        this.email = email;
        this.unsubscribeToken = UUID.randomUUID().toString();
    }

    public static EmailSubscriber create(String email) {
        return new EmailSubscriber(email);
    }

    public void verify(LocalDateTime at) {
        if (verifiedAt == null) {
            this.verifiedAt = at;
        }
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
