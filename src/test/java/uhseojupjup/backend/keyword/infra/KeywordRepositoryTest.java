package uhseojupjup.backend.keyword.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import uhseojupjup.backend.config.JpaAuditingConfig;
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.keyword.domain.KeywordAlias;
import uhseojupjup.backend.support.MySqlTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class KeywordRepositoryTest extends MySqlTestSupport {

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
