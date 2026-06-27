package uhseojupjup.backend.keyword.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhseojupjup.backend.keyword.domain.KeywordAlias;

import java.util.List;

public interface KeywordAliasRepository extends JpaRepository<KeywordAlias, Long> {

    List<KeywordAlias> findByKeywordId(Long keywordId);

    List<KeywordAlias> findByAliasContainingIgnoreCase(String alias);
}
