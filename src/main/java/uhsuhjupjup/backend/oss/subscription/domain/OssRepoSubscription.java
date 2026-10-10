package uhsuhjupjup.backend.oss.subscription.domain;

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
import uhsuhjupjup.backend.common.domain.BaseEntity;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.util.Set;

@Entity
@Table(name = "oss_repo_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssRepoSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private OssRepo repo;

    @Column(name = "difficulty_mask", nullable = false)
    private int difficultyMask;

    private OssRepoSubscription(Member member, OssRepo repo, int difficultyMask) {
        this.member = member;
        this.repo = repo;
        this.difficultyMask = difficultyMask;
    }

    public static OssRepoSubscription create(Member member, OssRepo repo, Set<OssIssueDifficulty> difficulties) {
        return new OssRepoSubscription(member, repo, OssDifficultyMask.of(difficulties));
    }

    public void changeDifficulties(Set<OssIssueDifficulty> difficulties) {
        this.difficultyMask = OssDifficultyMask.of(difficulties);
    }

    public Set<OssIssueDifficulty> difficulties() {
        return OssDifficultyMask.difficultiesOf(difficultyMask);
    }
}
