package uhsuhjupjup.backend.oss.repo.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.util.Optional;

public interface OssRepoRepository extends JpaRepository<OssRepo, Long> {

    Optional<OssRepo> findByGithubId(Long githubId);

    Optional<OssRepo> findByFullNameKey(String fullNameKey);
}
