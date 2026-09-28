package uhsuhjupjup.backend.oss.repo.domain;

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
import uhsuhjupjup.backend.common.domain.BaseEntity;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Entity
@Table(name = "oss_repo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssRepo extends BaseEntity {

    private static final int MAX_CATEGORIES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false)
    private Long githubId;

    @Column(name = "full_name", nullable = false, length = 140)
    private String fullName;

    @Column(name = "full_name_key", nullable = false, length = 140)
    private String fullNameKey;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "summary_ko", columnDefinition = "TEXT")
    private String summaryKo;

    @Column(name = "summary_en", columnDefinition = "TEXT")
    private String summaryEn;

    @Column(name = "primary_language", length = 100)
    private String primaryLanguage;

    @Column(name = "stars", nullable = false)
    private int stars;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OssRepoStatus status;

    private OssRepo(Long githubId, String fullName, String description, String primaryLanguage, int stars) {
        this.githubId = githubId;
        this.fullName = fullName;
        this.fullNameKey = fullName.toLowerCase(Locale.ROOT);
        this.description = description;
        this.primaryLanguage = primaryLanguage;
        this.stars = stars;
        this.status = OssRepoStatus.ACTIVE;
    }

    public static OssRepo create(Long githubId, String fullName, String description, String primaryLanguage,
                                 int stars) {
        return new OssRepo(githubId, fullName, description, primaryLanguage, stars);
    }

    public List<OssRepoCategory> linkCategories(Collection<OssCategory> categories) {
        Collection<OssCategory> distinct = distinctByCode(categories);
        if (distinct.size() > MAX_CATEGORIES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return distinct.stream()
                .map(category -> OssRepoCategory.of(this, category))
                .toList();
    }

    private static Collection<OssCategory> distinctByCode(Collection<OssCategory> categories) {
        Map<String, OssCategory> byCode = new LinkedHashMap<>();
        categories.forEach(category -> byCode.putIfAbsent(category.getCode(), category));
        return byCode.values();
    }
}
