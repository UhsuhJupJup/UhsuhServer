package uhsuhjupjup.backend.oss.repo.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoCategory;

import java.util.Collection;
import java.util.List;

public interface OssRepoCategoryRepository extends JpaRepository<OssRepoCategory, Long> {

    List<OssRepoCategory> findAllByRepoId(Long repoId);

    @Query("select rc from OssRepoCategory rc join fetch rc.category where rc.repo.id in :repoIds order by rc.category.id")
    List<OssRepoCategory> findWithCategoryByRepoIdIn(Collection<Long> repoIds);
}
