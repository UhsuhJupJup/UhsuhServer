package uhseojupjup.backend.blog.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhseojupjup.backend.blog.domain.Blog;

import java.util.List;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    List<Blog> findByActiveTrueOrderByIdAsc();
}
