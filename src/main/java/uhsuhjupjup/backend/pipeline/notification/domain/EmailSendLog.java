package uhsuhjupjup.backend.pipeline.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_send_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailSendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 20)
    private RecipientType recipientType;

    @Column(name = "article_count", nullable = false)
    private int articleCount;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    private EmailSendLog(String email, RecipientType recipientType, int articleCount, String subject) {
        this.email = email;
        this.recipientType = recipientType;
        this.articleCount = articleCount;
        this.subject = subject;
    }

    public static EmailSendLog of(String email, RecipientType recipientType, int articleCount, String subject) {
        return new EmailSendLog(email, recipientType, articleCount, subject);
    }
}
