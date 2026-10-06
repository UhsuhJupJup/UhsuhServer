package uhsuhjupjup.backend.oss.issue.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OssIssueGradeRepository extends JpaRepository<OssIssueGrade, Long> {

    Optional<OssIssueGrade> findFirstByIssueIdOrderByIdDesc(Long issueId);

    @Query("""
            select g from OssIssueGrade g
            where g.issue.id in :issueIds
            and g.id = (select max(latest.id) from OssIssueGrade latest where latest.issue = g.issue)
            """)
    List<OssIssueGrade> findLatestByIssueIdIn(Collection<Long> issueIds);
}
