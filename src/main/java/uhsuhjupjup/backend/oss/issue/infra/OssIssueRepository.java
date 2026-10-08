package uhsuhjupjup.backend.oss.issue.infra;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OssIssueRepository extends JpaRepository<OssIssue, Long> {

    Optional<OssIssue> findByGithubIssueId(Long githubIssueId);

    @Query("""
            select i from OssIssue i
            join fetch i.repo r
            where i.id = :id and r.status = :repoStatus
            """)
    Optional<OssIssue> findWithRepoByIdAndRepoStatus(Long id, OssRepoStatus repoStatus);

    List<OssIssue> findAllByGithubIssueIdIn(Collection<Long> githubIssueIds);

    @Query("""
            select i from OssIssue i
            where i.repo.id = :repoId
            and not exists (
                select g.id from OssIssueGrade g
                where g.issue = i and g.sourceHash = i.bodyHash)
            and (i.gradingFailureSourceHash is null
                or i.gradingFailureSourceHash <> i.bodyHash
                or i.gradingFailures < :maxFailures)
            order by i.githubCreatedAt desc, i.id desc
            """)
    List<OssIssue> findGradingCandidates(Long repoId, int maxFailures, Limit limit);

    @Modifying
    @Query("""
            update OssIssue i
            set i.gradingFailures = case when i.gradingFailureSourceHash = :sourceHash
                    then i.gradingFailures + 1 else 1 end,
                i.gradingFailureSourceHash = :sourceHash
            where i.id = :issueId
            """)
    int recordGradingFailure(Long issueId, String sourceHash);

    @Modifying
    @Query("""
            update OssIssue i
            set i.gradingFailures = 0,
                i.gradingFailureSourceHash = null
            where i.id = :issueId
            """)
    int clearGradingFailures(Long issueId);
}
