package uhsuhjupjup.backend.oss.repo.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
class OssRepoRepositoryTest {

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByGithubId_returnsSavedRepo() {
        Long id = ossRepoRepository.saveAndFlush(repo(1L, "spring-projects/spring-boot")).getId();
        entityManager.clear();

        assertThat(ossRepoRepository.findByGithubId(1L))
                .map(OssRepo::getId)
                .hasValue(id);
    }

    @Test
    void findByFullNameKey_returnsSavedRepo() {
        Long id = ossRepoRepository.saveAndFlush(repo(1L, "Spring-Projects/Spring-Boot")).getId();
        entityManager.clear();

        assertThat(ossRepoRepository.findByFullNameKey("spring-projects/spring-boot"))
                .map(OssRepo::getId)
                .hasValue(id);
    }

    @Test
    void findForUpdateById_returnsSavedRepoOrNothing() {
        Long id = ossRepoRepository.saveAndFlush(repo(1L, "spring-projects/spring-boot")).getId();
        entityManager.clear();

        assertThat(ossRepoRepository.findForUpdateById(id))
                .map(OssRepo::getFullName)
                .hasValue("spring-projects/spring-boot");
        assertThat(ossRepoRepository.findForUpdateById(id + 1)).isEmpty();
    }

    @Test
    void create_lowercasesFullNameKeyAndStartsActive() {
        Long id = ossRepoRepository.saveAndFlush(repo(1L, "Spring-Projects/Spring-Boot")).getId();
        entityManager.clear();

        OssRepo loaded = ossRepoRepository.findById(id).orElseThrow();

        assertThat(loaded.getFullName()).isEqualTo("Spring-Projects/Spring-Boot");
        assertThat(loaded.getFullNameKey()).isEqualTo("spring-projects/spring-boot");
        assertThat(loaded.getStatus()).isEqualTo(OssRepoStatus.ACTIVE);
    }

    @Test
    void create_startsWithoutSummaries() {
        OssRepo saved = ossRepoRepository.saveAndFlush(repo(1L, "spring-projects/spring-boot"));
        entityManager.clear();

        OssRepo loaded = ossRepoRepository.findById(saved.getId()).orElseThrow();

        assertThat(saved).extracting(OssRepo::getSummaryKo, OssRepo::getSummaryEn).containsOnlyNulls();
        assertThat(loaded).extracting(OssRepo::getSummaryKo, OssRepo::getSummaryEn).containsOnlyNulls();
    }

    @Test
    void fullNameDifferingOnlyInCase_violatesUniqueConstraint() {
        ossRepoRepository.saveAndFlush(repo(1L, "Spring-Projects/Spring-Boot"));

        assertThatThrownBy(() -> ossRepoRepository.saveAndFlush(repo(2L, "spring-projects/spring-boot")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_oss_repo_full_name_key");
    }

    @Test
    void duplicateGithubId_violatesUniqueConstraint() {
        ossRepoRepository.saveAndFlush(repo(1L, "spring-projects/spring-boot"));

        assertThatThrownBy(() -> ossRepoRepository.saveAndFlush(repo(1L, "spring-projects/spring-boot-renamed")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_oss_repo_github_id");
    }

    @Test
    void missingDescriptionAndLanguage_areStoredAsNull() {
        Long id = ossRepoRepository.saveAndFlush(OssRepo.create(1L, "octocat/empty", null, null, 0)).getId();
        entityManager.clear();

        OssRepo loaded = ossRepoRepository.findById(id).orElseThrow();

        assertThat(loaded.getDescription()).isNull();
        assertThat(loaded.getPrimaryLanguage()).isNull();
    }

    @Test
    void descriptionLongerThanGitHubLimit_isStored() {
        String legacyDescription = "a".repeat(1_000);
        Long id = ossRepoRepository.saveAndFlush(
                OssRepo.create(1L, "octocat/legacy", legacyDescription, "C", 1_000)).getId();
        entityManager.clear();

        assertThat(ossRepoRepository.findById(id).orElseThrow().getDescription()).isEqualTo(legacyDescription);
    }

    @Test
    void auditing_setsCreatedAndUpdatedAt() {
        OssRepo saved = ossRepoRepository.saveAndFlush(repo(1L, "spring-projects/spring-boot"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private OssRepo repo(Long githubId, String fullName) {
        return OssRepo.create(githubId, fullName, "Spring Boot", "Java", 80_000);
    }
}
