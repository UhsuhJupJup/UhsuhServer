package uhsuhjupjup.backend.oss.issue.infra;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
class OssIssueRepositoryTest {

    private static final long GITHUB_ISSUE_ID_BEYOND_INT = 5_611_573_057L;
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 28, 17, 23, 29);
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Autowired
    private OssIssueRepository ossIssueRepository;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findByGithubIssueId_returnsSavedIssueWithItsColumns() {
        OssRepo repo = ossRepoRepository.save(repo(4542716L, "NixOS/nixpkgs"));
        Long id = ossIssueRepository.saveAndFlush(OssIssue.create(
                repo, GITHUB_ISSUE_ID_BEYOND_INT, 567_775, "Retry interval is ignored", "abc", OPENED_AT)).getId();
        entityManager.clear();

        OssIssue loaded = ossIssueRepository.findByGithubIssueId(GITHUB_ISSUE_ID_BEYOND_INT).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getRepo().getId()).isEqualTo(repo.getId());
        assertThat(loaded.getGithubIssueId()).isEqualTo(GITHUB_ISSUE_ID_BEYOND_INT);
        assertThat(loaded.getNumber()).isEqualTo(567_775);
        assertThat(loaded.getTitle()).isEqualTo("Retry interval is ignored");
        assertThat(loaded.getBodyHash()).isEqualTo(ABC_SHA256);
        assertThat(loaded.getGithubCreatedAt()).isEqualTo(OPENED_AT);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void findByGithubIssueId_unknownId_returnsEmpty() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossIssueRepository.saveAndFlush(issue(repo, 1L, 1));
        entityManager.clear();

        assertThat(ossIssueRepository.findByGithubIssueId(2L)).isEmpty();
    }

    @Test
    void findByGithubIssueId_loadsIssueWithoutLoadingRepo() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossIssueRepository.saveAndFlush(issue(repo, 1L, 1));
        entityManager.clear();

        OssIssue loaded = ossIssueRepository.findByGithubIssueId(1L).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getRepo())).isFalse();
    }

    @Test
    void duplicateGithubIssueId_violatesUniqueConstraintAcrossRepos() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossIssueRepository.saveAndFlush(issue(springBoot, 1L, 10));

        assertThatThrownBy(() -> ossIssueRepository.saveAndFlush(issue(react, 1L, 20)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_oss_issue_github_issue_id");
    }

    @Test
    void sameNumberInDifferentRepos_isStored() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossIssueRepository.saveAndFlush(issue(springBoot, 1L, 1));
        ossIssueRepository.saveAndFlush(issue(react, 2L, 1));
        entityManager.clear();

        assertThat(ossIssueRepository.findAll()).extracting(OssIssue::getNumber).containsExactly(1, 1);
    }

    @Test
    void titleOfKoreanAndEmojiOverGitHubLimit_isStoredCutTo256CodePoints() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Long id = ossIssueRepository.saveAndFlush(
                OssIssue.create(repo, 1L, 1, "🐛버그".repeat(100), null, OPENED_AT)).getId();
        entityManager.clear();

        String stored = ossIssueRepository.findById(id).orElseThrow().getTitle();

        assertThat(stored).isEqualTo("🐛버그".repeat(85) + "🐛");
        assertThat(stored.codePointCount(0, stored.length())).isEqualTo(256);
    }

    @Test
    void deletingRepo_deletesItsIssuesOnly() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        Long springBootIssue = ossIssueRepository.save(issue(springBoot, 1L, 1)).getId();
        Long reactIssue = ossIssueRepository.save(issue(react, 2L, 1)).getId();
        entityManager.flush();
        entityManager.clear();

        ossRepoRepository.deleteById(springBoot.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueRepository.findById(springBootIssue)).isEmpty();
        assertThat(ossIssueRepository.findById(reactIssue)).isPresent();
    }

    @Test
    void issueTable_hasNoColumnForRawBody() {
        List<String> bodyColumns = jdbcTemplate.queryForList("""
                select concat(column_name, ' ', column_type) from information_schema.columns
                where table_schema = database() and table_name = 'oss_issue' and column_name like '%body%'
                """, String.class);

        assertThat(bodyColumns).containsExactly("body_hash varchar(64)");
    }

    private OssIssue issue(OssRepo repo, long githubIssueId, int number) {
        return OssIssue.create(repo, githubIssueId, number, "Retry interval is ignored", "abc", OPENED_AT);
    }

    private OssRepo repo(Long githubId, String fullName) {
        return OssRepo.create(githubId, fullName, null, "Java", 1_000);
    }
}
