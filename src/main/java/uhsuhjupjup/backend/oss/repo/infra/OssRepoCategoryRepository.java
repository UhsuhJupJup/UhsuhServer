package uhsuhjupjup.backend.oss.repo.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoCategory;

import java.util.List;

public interface OssRepoCategoryRepository extends JpaRepository<OssRepoCategory, Long> {

    List<OssRepoCategory> findAllByRepoId(Long repoId);
}
