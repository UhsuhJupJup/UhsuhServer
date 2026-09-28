package uhsuhjupjup.backend.oss.issue.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;

import java.util.Optional;

public interface OssIssueRepository extends JpaRepository<OssIssue, Long> {

    Optional<OssIssue> findByGithubIssueId(Long githubIssueId);
}
