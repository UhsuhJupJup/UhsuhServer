package uhsuhjupjup.backend.keyword.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.keyword.domain.Keyword;

import java.util.List;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    List<Keyword> findAllByOrderByNameAsc();

    @Query("select distinct k from Keyword k left join KeywordAlias ka on ka.keywordId = k.id "
            + "where lower(k.name) like lower(concat('%', :keyword, '%')) "
            + "or lower(ka.alias) like lower(concat('%', :keyword, '%')) "
            + "order by k.name")
    List<Keyword> searchByNameOrAlias(String keyword);
}
