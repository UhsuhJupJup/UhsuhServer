package uhsuhjupjup.backend.oss.pipeline.sync.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssRepoSyncState;

import java.util.Optional;

public interface OssRepoSyncStateRepository extends JpaRepository<OssRepoSyncState, Long> {

    Optional<OssRepoSyncState> findByRepoId(Long repoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OssRepoSyncState> findForUpdateByRepoId(Long repoId);
}
