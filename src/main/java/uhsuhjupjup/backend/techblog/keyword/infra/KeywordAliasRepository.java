package uhsuhjupjup.backend.techblog.keyword.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.techblog.keyword.domain.KeywordAlias;

public interface KeywordAliasRepository extends JpaRepository<KeywordAlias, Long> {
}
