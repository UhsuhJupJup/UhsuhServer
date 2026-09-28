package uhsuhjupjup.backend.oss.repo.infra;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoCategory;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@MySqlDataJpaTest
class OssRepoCategoryRepositoryTest {

    @Autowired
    private OssRepoCategoryRepository ossRepoCategoryRepository;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private OssCategoryRepository ossCategoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findAllByRepoId_returnsOnlyThatReposLinks() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossRepoCategoryRepository.saveAll(springBoot.linkCategories(categories("backend", "devtools")));
        ossRepoCategoryRepository.saveAll(react.linkCategories(categories("web-frontend")));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossRepoCategoryRepository.findAllByRepoId(springBoot.getId()))
                .extracting(link -> link.getCategory().getCode())
                .containsExactlyInAnyOrder("backend", "devtools");
    }

    @Test
    void sameRepoAndCategoryTwice_violatesUniqueConstraint() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossRepoCategoryRepository.saveAndFlush(onlyLink(repo, "backend"));

        assertThatThrownBy(() -> ossRepoCategoryRepository.saveAndFlush(onlyLink(repo, "backend")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_oss_repo_category");
    }

    @Test
    void deletingRepo_deletesItsLinksOnly() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossRepoCategoryRepository.saveAll(springBoot.linkCategories(categories("backend", "devtools")));
        ossRepoCategoryRepository.saveAll(react.linkCategories(categories("web-frontend")));
        entityManager.flush();
        entityManager.clear();

        ossRepoRepository.deleteById(springBoot.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ossRepoCategoryRepository.findAllByRepoId(springBoot.getId())).isEmpty();
        assertThat(ossRepoCategoryRepository.findAllByRepoId(react.getId())).hasSize(1);
    }

    @Test
    void deletingCategory_deletesItsLinksOnly() {
        OssCategory robotics = ossCategoryRepository.save(OssCategory.create("robotics", "로보틱스", "Robotics"));
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossRepoCategoryRepository.saveAll(repo.linkCategories(List.of(robotics, categories("backend").get(0))));
        entityManager.flush();
        entityManager.clear();

        ossCategoryRepository.deleteById(robotics.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ossRepoCategoryRepository.findAllByRepoId(repo.getId()))
                .extracting(link -> link.getCategory().getCode())
                .containsExactly("backend");
    }

    @Test
    void findWithCategoryByRepoIdIn_returnsGivenReposLinksInCategoryOrderWithCategoriesLoaded() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        OssRepo kafka = ossRepoRepository.save(repo(3L, "apache/kafka"));
        ossRepoCategoryRepository.saveAll(springBoot.linkCategories(inGivenOrder("devtools", "backend")));
        ossRepoCategoryRepository.saveAll(react.linkCategories(categories("web-frontend")));
        ossRepoCategoryRepository.saveAll(kafka.linkCategories(categories("data")));
        entityManager.flush();
        entityManager.clear();

        List<OssRepoCategory> links =
                ossRepoCategoryRepository.findWithCategoryByRepoIdIn(List.of(springBoot.getId(), react.getId()));

        assertThat(links).allSatisfy(link -> assertThat(Hibernate.isInitialized(link.getCategory())).isTrue());
        assertThat(links)
                .extracting(link -> link.getRepo().getId(), link -> link.getCategory().getCode())
                .containsExactly(
                        tuple(react.getId(), "web-frontend"),
                        tuple(springBoot.getId(), "backend"),
                        tuple(springBoot.getId(), "devtools"));
    }

    private List<OssCategory> categories(String... codes) {
        return ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of(codes));
    }

    private List<OssCategory> inGivenOrder(String... codes) {
        return Stream.of(codes)
                .map(code -> categories(code).get(0))
                .toList();
    }

    private OssRepoCategory onlyLink(OssRepo repo, String code) {
        return repo.linkCategories(categories(code)).get(0);
    }

    private OssRepo repo(Long githubId, String fullName) {
        return OssRepo.create(githubId, fullName, null, "Java", 1_000);
    }
}
