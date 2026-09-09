package uhsuhjupjup.backend.keyword.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.domain.KeywordAlias;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
class KeywordRepositoryTest {

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private KeywordAliasRepository keywordAliasRepository;

    @Test
    void findAllByOrderByNameAsc_ordersByName() {
        keywordRepository.saveAll(List.of(Keyword.create("Redis"), Keyword.create("Kafka")));

        assertThat(keywordRepository.findAllByOrderByNameAsc())
                .extracting(Keyword::getName)
                .containsExactly("Kafka", "Redis");
    }

    @Test
    void searchByNameOrAlias_matchesByName() {
        keywordRepository.saveAll(List.of(Keyword.create("MySQL"), Keyword.create("Redis")));

        assertThat(keywordRepository.searchByNameOrAlias("sql"))
                .extracting(Keyword::getName)
                .containsExactly("MySQL");
    }

    @Test
    void searchByNameOrAlias_matchesByAlias() {
        Keyword mysql = keywordRepository.save(Keyword.create("MySQL"));
        keywordRepository.save(Keyword.create("Redis"));
        keywordAliasRepository.save(KeywordAlias.create(mysql.getId(), "마이에스큐엘"));

        assertThat(keywordRepository.searchByNameOrAlias("마이"))
                .extracting(Keyword::getName)
                .containsExactly("MySQL");
    }

    @Test
    void duplicateName_violatesUniqueConstraint() {
        keywordRepository.saveAndFlush(Keyword.create("Redis"));

        assertThatThrownBy(() -> keywordRepository.saveAndFlush(Keyword.create("Redis")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditing_setsCreatedAndUpdatedAt() {
        Keyword saved = keywordRepository.saveAndFlush(Keyword.create("Redis"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
