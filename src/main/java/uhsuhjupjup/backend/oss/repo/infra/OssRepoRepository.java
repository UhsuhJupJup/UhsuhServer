package uhsuhjupjup.backend.oss.repo.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.util.Optional;

public interface OssRepoRepository extends JpaRepository<OssRepo, Long> {

    Optional<OssRepo> findByGithubId(Long githubId);

    Optional<OssRepo> findByFullNameKey(String fullNameKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OssRepo> findForUpdateById(Long id);
}
