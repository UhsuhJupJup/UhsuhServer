package uhsuhjupjup.backend.oss.repo.domain;

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

@Entity
@Table(name = "oss_repo_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssRepoCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private OssRepo repo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private OssCategory category;

    private OssRepoCategory(OssRepo repo, OssCategory category) {
        this.repo = repo;
        this.category = category;
    }

    static OssRepoCategory of(OssRepo repo, OssCategory category) {
        return new OssRepoCategory(repo, category);
    }
}
