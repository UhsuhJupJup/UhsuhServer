package uhseojupjup.backend.keyword.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhseojupjup.backend.keyword.domain.KeywordAlias;

public interface KeywordAliasRepository extends JpaRepository<KeywordAlias, Long> {
}
