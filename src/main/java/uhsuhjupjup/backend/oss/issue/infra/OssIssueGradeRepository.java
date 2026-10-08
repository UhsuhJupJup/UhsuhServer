package uhsuhjupjup.backend.oss.issue.infra;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OssIssueGradeRepository extends JpaRepository<OssIssueGrade, Long> {

    @Query("""
            select g from OssIssueGrade g
            where g.id = (
                select max(matching.id) from OssIssueGrade matching
                join matching.issue issue
                where issue.id = :issueId and matching.sourceHash = issue.bodyHash)
            """)
    Optional<OssIssueGrade> findCurrentByIssueId(Long issueId);

    @Query("""
            select g from OssIssue i
            join OssIssueGrade g on g.id = (
                select max(matching.id) from OssIssueGrade matching
                where matching.issue = i and matching.sourceHash = i.bodyHash)
            join fetch g.issue
            where i.repo.id = :repoId
            and g.exclusion is null
            and g.difficulty in :difficulties
            and (:afterIssueId is null or i.githubCreatedAt < :afterGithubCreatedAt
                or (i.githubCreatedAt = :afterGithubCreatedAt and i.id < :afterIssueId))
            order by i.githubCreatedAt desc, i.id desc
            """)
    List<OssIssueGrade> findCurrentNotExcludedByRepoId(Long repoId, Collection<OssIssueDifficulty> difficulties,
                                                       LocalDateTime afterGithubCreatedAt, Long afterIssueId,
                                                       Limit limit);

    boolean existsByIssueIdAndSourceHash(Long issueId, String sourceHash);
}
