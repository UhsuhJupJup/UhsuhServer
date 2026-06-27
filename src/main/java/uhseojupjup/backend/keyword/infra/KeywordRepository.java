package uhseojupjup.backend.keyword.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhseojupjup.backend.keyword.domain.Keyword;

import java.util.List;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    List<Keyword> findAllByOrderByNameAsc();

    List<Keyword> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
