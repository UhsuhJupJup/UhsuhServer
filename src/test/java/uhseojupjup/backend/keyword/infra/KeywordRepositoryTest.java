package uhseojupjup.backend.keyword.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import uhseojupjup.backend.config.JpaAuditingConfig;
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.support.MySqlTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class KeywordRepositoryTest extends MySqlTestSupport {

    @Autowired
    private KeywordRepository keywordRepository;

    @Test
    void findAllByOrderByNameAsc_ordersByName() {
        keywordRepository.saveAll(List.of(Keyword.create("Redis"), Keyword.create("Kafka")));

        assertThat(keywordRepository.findAllByOrderByNameAsc())
                .extracting(Keyword::getName)
                .containsExactly("Kafka", "Redis");
    }

    @Test
    void findByNameContainingIgnoreCase_matchesCaseInsensitively() {
        keywordRepository.saveAll(List.of(Keyword.create("Kafka"), Keyword.create("Redis")));

        assertThat(keywordRepository.findByNameContainingIgnoreCaseOrderByNameAsc("ka"))
                .extracting(Keyword::getName)
                .containsExactly("Kafka");
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
