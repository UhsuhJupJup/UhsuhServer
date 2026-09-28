package uhsuhjupjup.backend.oss.repo.application;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import uhsuhjupjup.backend.oss.repo.application.dto.OssCategoryResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoPageResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
@Import(OssRepoService.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class OssRepoServiceQueryCountTest {

    private static final int REPO_COUNT = 6;

    @Autowired
    private OssRepoService ossRepoService;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private OssCategoryRepository ossCategoryRepository;

    @Autowired
    private OssRepoCategoryRepository ossRepoCategoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        for (int i = 1; i <= REPO_COUNT; i++) {
            OssRepo repo = ossRepoRepository.save(OssRepo.create((long) i, "owner/repo-" + i, null, "Java", i));
            ossRepoCategoryRepository.saveAll(repo.linkCategories(
                    ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("backend", "devtools"))));
        }
        entityManager.flush();
        entityManager.clear();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void explore_readsPageAndItsCategoriesInTwoStatementsWhateverThePageSize() {
        OssRepoPageResult onePerPage = ossRepoService.explore(null, null, null, null, null, 1);
        long statementsForOne = statistics.getPrepareStatementCount();
        entityManager.clear();
        statistics.clear();

        OssRepoPageResult allInOnePage = ossRepoService.explore(null, null, null, null, null, REPO_COUNT);

        assertThat(onePerPage.items()).hasSize(1);
        assertThat(allInOnePage.items()).hasSize(REPO_COUNT).allSatisfy(item ->
                assertThat(item.categories()).extracting(OssCategoryResult::code)
                        .containsExactly("backend", "devtools"));
        assertThat(statementsForOne).isEqualTo(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void explore_withCategoryFilter_addsOnlyTheCategoryLookup() {
        OssRepoPageResult result = ossRepoService.explore("backend", null, null, null, null, REPO_COUNT);

        assertThat(result.items()).hasSize(REPO_COUNT);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void getDetail_readsRepoAndItsCategoriesInCatalogOrderInTwoStatements() {
        OssRepo nextJs = ossRepoRepository.save(OssRepo.create(100L, "vercel/next.js", null, "JavaScript", 7));
        ossRepoCategoryRepository.saveAll(nextJs.linkCategories(List.of(category("backend"), category("web-frontend"))));
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        OssRepoResult result = ossRepoService.getDetail(nextJs.getId());

        assertThat(result.fullName()).isEqualTo("vercel/next.js");
        assertThat(result.categories()).extracting(OssCategoryResult::code)
                .containsExactly("web-frontend", "backend");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    private OssCategory category(String code) {
        return ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of(code)).get(0);
    }
}
