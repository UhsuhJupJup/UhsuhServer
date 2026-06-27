package uhsuhjupjup.backend.subscription.domain;

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
import uhsuhjupjup.backend.member.domain.Member;

@Entity
@Table(name = "keyword_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeywordSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    private KeywordSubscription(Member member, Keyword keyword) {
        this.member = member;
        this.keyword = keyword;
    }

    public static KeywordSubscription of(Member member, Keyword keyword) {
        return new KeywordSubscription(member, keyword);
    }
}
