package uhsuhjupjup.backend.techblog.blog.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.techblog.blog.domain.Blog;

import java.util.List;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    List<Blog> findByActiveTrueOrderByIdAsc();

    List<Blog> findAllByOrderByIdAsc();

    boolean existsByDomain(String domain);
}
