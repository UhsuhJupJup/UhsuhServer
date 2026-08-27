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
@Table(name = "notification_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 30)
    private RecipientType recipientType;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String body;

    @Column(name = "article_count", nullable = false)
    private int articleCount;

    @Column(name = "unsubscribe_url", length = 500)
    private String unsubscribeUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    private NotificationOutbox(String recipient, RecipientType recipientType, int articleCount, String subject,
                              String body, String unsubscribeUrl, LocalDateTime nextAttemptAt) {
        this.recipient = recipient;
        this.recipientType = recipientType;
        this.articleCount = articleCount;
        this.subject = subject;
        this.body = body;
        this.unsubscribeUrl = unsubscribeUrl;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = nextAttemptAt;
    }

    public static NotificationOutbox pending(String recipient, RecipientType recipientType, int articleCount,
                                             String subject, String body, String unsubscribeUrl,
                                             LocalDateTime sendAfter) {
        return new NotificationOutbox(recipient, recipientType, articleCount, subject, body, unsubscribeUrl, sendAfter);
    }

    public void markSent(LocalDateTime at) {
        this.status = OutboxStatus.SENT;
        this.sentAt = at;
        this.lastError = null;
    }

    public void markRetry(String error, LocalDateTime nextAttemptAt) {
        this.attempts++;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = truncate(error);
        this.status = OutboxStatus.FAILED;
    }

    public void requeue(LocalDateTime at) {
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = at;
        this.lastError = null;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
