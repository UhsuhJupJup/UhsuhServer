package uhsuhjupjup.backend.emailsubscription.domain;

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
import uhsuhjupjup.backend.common.domain.BaseEntity;
import uhsuhjupjup.backend.keyword.domain.Keyword;

@Entity
@Table(name = "email_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_subscriber_id", nullable = false)
    private EmailSubscriber emailSubscriber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    private EmailSubscription(EmailSubscriber emailSubscriber, Keyword keyword) {
        this.emailSubscriber = emailSubscriber;
        this.keyword = keyword;
    }

    public static EmailSubscription of(EmailSubscriber emailSubscriber, Keyword keyword) {
        return new EmailSubscription(emailSubscriber, keyword);
    }
}
