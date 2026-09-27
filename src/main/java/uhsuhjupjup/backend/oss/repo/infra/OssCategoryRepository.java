package uhsuhjupjup.backend.oss.repo.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;

import java.util.List;

public interface OssCategoryRepository extends JpaRepository<OssCategory, Long> {

    List<OssCategory> findAllByOrderByIdAsc();
}
