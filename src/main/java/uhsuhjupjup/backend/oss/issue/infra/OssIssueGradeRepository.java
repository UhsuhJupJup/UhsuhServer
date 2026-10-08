package uhsuhjupjup.backend.oss.issue.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;

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

    boolean existsByIssueIdAndSourceHash(Long issueId, String sourceHash);
}
