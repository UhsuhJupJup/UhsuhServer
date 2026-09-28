package uhsuhjupjup.backend.oss.pipeline.sync.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssRepoSyncState;

import java.util.Optional;

public interface OssRepoSyncStateRepository extends JpaRepository<OssRepoSyncState, Long> {

    Optional<OssRepoSyncState> findByRepoId(Long repoId);
}
