package uhsuhjupjup.backend.keyword.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.keyword.domain.KeywordAlias;

public interface KeywordAliasRepository extends JpaRepository<KeywordAlias, Long> {
}
