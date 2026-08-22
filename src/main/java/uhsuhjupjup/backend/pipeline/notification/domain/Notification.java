package uhsuhjupjup.backend.pipeline.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.member.domain.Member;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_subscriber_id")
    private EmailSubscriber emailSubscriber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "matched_keywords", length = 255)
    private String matchedKeywords;

    private Notification(Member member, EmailSubscriber emailSubscriber, Article article, String matchedKeywords) {
        this.member = member;
        this.emailSubscriber = emailSubscriber;
        this.article = article;
        this.matchedKeywords = matchedKeywords;
    }

    public static Notification of(Member member, Article article, String matchedKeywords) {
        return new Notification(member, null, article, matchedKeywords);
    }

    public static Notification ofEmail(EmailSubscriber emailSubscriber, Article article, String matchedKeywords) {
        return new Notification(null, emailSubscriber, article, matchedKeywords);
    }
}
