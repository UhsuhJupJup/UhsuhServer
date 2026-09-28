package uhsuhjupjup.backend.oss.repo.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;

import java.util.List;
import java.util.Optional;

public interface OssRepoRepository extends JpaRepository<OssRepo, Long> {

    Optional<OssRepo> findByGithubId(Long githubId);

    Optional<OssRepo> findByFullNameKey(String fullNameKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OssRepo> findForUpdateById(Long id);

    Optional<OssRepo> findByIdAndStatus(Long id, OssRepoStatus status);

    @Query("""
            select r from OssRepo r
            where r.status = :status
            and (:categoryId is null or exists (
                select 1 from OssRepoCategory rc where rc.repo = r and rc.category.id = :categoryId))
            and (:language is null or r.primaryLanguage = :language)
            and (:pattern is null
                or lower(r.fullName) like lower(:pattern) escape '!'
                or lower(r.description) like lower(:pattern) escape '!')
            and (:afterId is null or r.stars < :afterStars or (r.stars = :afterStars and r.id < :afterId))
            order by r.stars desc, r.id desc
            """)
    List<OssRepo> findPageSortedByStars(OssRepoStatus status, Long categoryId, String language, String pattern,
                                        Integer afterStars, Long afterId, Limit limit);

    @Query("""
            select r from OssRepo r
            where r.status = :status
            and (:categoryId is null or exists (
                select 1 from OssRepoCategory rc where rc.repo = r and rc.category.id = :categoryId))
            and (:language is null or r.primaryLanguage = :language)
            and (:pattern is null
                or lower(r.fullName) like lower(:pattern) escape '!'
                or lower(r.description) like lower(:pattern) escape '!')
            and (:afterId is null or r.fullNameKey > :afterKey or (r.fullNameKey = :afterKey and r.id > :afterId))
            order by r.fullNameKey asc, r.id asc
            """)
    List<OssRepo> findPageSortedByName(OssRepoStatus status, Long categoryId, String language, String pattern,
                                       String afterKey, Long afterId, Limit limit);
}
