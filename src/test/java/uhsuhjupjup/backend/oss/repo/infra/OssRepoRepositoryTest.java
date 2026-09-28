package uhsuhjupjup.backend.oss.repo.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
class OssRepoRepositoryTest {

    private static final Limit PAGE = Limit.of(50);

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private OssCategoryRepository ossCategoryRepository;

    @Autowired
    private OssRepoCategoryRepository ossRepoCategoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private long nextGithubId = 1;

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

    @Test
    void findPageSortedByStars_ordersByStarsThenNewerIdFirstAndSkipsSuspended() {
        Long small = save("octocat/small", 10);
        Long olderTie = save("octocat/older-tie", 300);
        Long top = save("octocat/top", 500);
        Long newerTie = save("octocat/newer-tie", 300);
        saveSuspended("octocat/suspended", 1_000);

        assertThat(idsByStars(null, null, null, null, null)).containsExactly(top, newerTie, olderTie, small);
    }

    @Test
    void findPageSortedByStars_afterPosition_returnsOnlyReposPastIt() {
        Long small = save("octocat/small", 10);
        Long olderTie = save("octocat/older-tie", 300);
        save("octocat/top", 500);
        Long newerTie = save("octocat/newer-tie", 300);

        assertThat(idsByStars(null, null, null, 300, newerTie)).containsExactly(olderTie, small);
        assertThat(idsByStars(null, null, null, 300, olderTie)).containsExactly(small);
        assertThat(idsByStars(null, null, null, 10, small)).isEmpty();
    }

    @Test
    void findPageSortedByStars_limit_capsRowCount() {
        save("octocat/a", 3);
        save("octocat/b", 2);
        save("octocat/c", 1);

        assertThat(ossRepoRepository.findPageSortedByStars(
                        OssRepoStatus.ACTIVE, null, null, null, null, null, Limit.of(2)))
                .extracting(OssRepo::getFullName)
                .containsExactly("octocat/a", "octocat/b");
    }

    @Test
    void findPageSortedByName_ordersByNameIgnoringCaseAndSkipsSuspended() {
        Long zeta = save("zeta/app", 1);
        Long beta = save("Beta/lib", 1);
        Long alpha = save("alpha/tool", 1);
        saveSuspended("aaa/suspended", 1);

        assertThat(idsByName(null, null, null, null, null)).containsExactly(alpha, beta, zeta);
    }

    @Test
    void findPageSortedByName_afterPosition_returnsOnlyReposPastIt() {
        Long zeta = save("zeta/app", 1);
        Long beta = save("Beta/lib", 1);
        save("alpha/tool", 1);

        assertThat(idsByName(null, null, null, "beta/lib", beta)).containsExactly(zeta);
        assertThat(idsByName(null, null, null, "zeta/app", zeta)).isEmpty();
    }

    @Test
    void findPage_categoryFilter_keepsOnlyReposLinkedToThatCategory() {
        Long springBoot = save("spring-projects/spring-boot", 3, "backend", "devtools");
        Long flask = save("pallets/flask", 2, "backend");
        save("facebook/react", 1, "web-frontend");
        save("octocat/Hello-World", 0);
        Long backendId = ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("backend")).get(0).getId();

        assertThat(idsByStars(backendId, null, null, null, null)).containsExactly(springBoot, flask);
        assertThat(idsByName(backendId, null, null, null, null)).containsExactly(flask, springBoot);
    }

    @Test
    void findPage_languageFilter_matchesWholeLanguageIgnoringCaseAndAccentsByColumnCollation() {
        Long java = saveWith("octocat/java", null, "Java", 2);
        saveWith("octocat/javascript", null, "JavaScript", 1);
        Long accented = saveWith("octocat/accented", null, "Français", 1);
        saveWith("octocat/none", null, null, 0);

        assertThat(idsByStars(null, "java", null, null, null)).containsExactly(java);
        assertThat(idsByStars(null, "francais", null, null, null)).containsExactly(accented);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("likePatterns")
    void findPage_pattern_matchesNameOrDescriptionWithBangAsEscape(String pattern, List<String> expected) {
        saveWith("octocat/percent", "100% open source", null, 0);
        saveWith("octocat/thousand", "1000 stars", null, 0);
        saveWith("octocat/snake", "snake_case helper", null, 0);
        saveWith("octocat/snakex", "snakeXcase helper", null, 0);
        saveWith("octocat/bang", "Hello! world", null, 0);
        saveWith("octocat/plain", "Hello world", null, 0);
        saveWith("octocat/windows", "C:\\tools\\path", null, 0);
        saveWith("octocat/slash", "C:/tools/path", null, 0);
        saveWith("octocat/latte", "Café au lait", null, 0);
        saveWith("Spring-Projects/Spring-Boot", null, null, 0);

        assertThat(ossRepoRepository.findPageSortedByStars(
                        OssRepoStatus.ACTIVE, null, null, pattern, null, null, PAGE))
                .extracting(OssRepo::getFullName)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    static Stream<Arguments> likePatterns() {
        return Stream.of(
                Arguments.of("%100!%%", List.of("octocat/percent")),
                Arguments.of("%snake!_case%", List.of("octocat/snake")),
                Arguments.of("%o!!%", List.of("octocat/bang")),
                Arguments.of("%\\tools%", List.of("octocat/windows")),
                Arguments.of("%HELPER%", List.of("octocat/snake", "octocat/snakex")),
                Arguments.of("%cafe%", List.of("octocat/latte")),
                Arguments.of("%spring-boot%", List.of("Spring-Projects/Spring-Boot")));
    }

    private OssRepo repo(Long githubId, String fullName) {
        return OssRepo.create(githubId, fullName, "Spring Boot", "Java", 80_000);
    }

    private Long save(String fullName, int stars, String... categoryCodes) {
        OssRepo repo = ossRepoRepository.save(OssRepo.create(nextGithubId++, fullName, null, "Java", stars));
        ossRepoCategoryRepository.saveAll(
                repo.linkCategories(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of(categoryCodes))));
        return repo.getId();
    }

    private Long saveWith(String fullName, String description, String language, int stars) {
        return ossRepoRepository.save(OssRepo.create(nextGithubId++, fullName, description, language, stars)).getId();
    }

    private void saveSuspended(String fullName, int stars) {
        OssRepo repo = OssRepo.create(nextGithubId++, fullName, null, "Java", stars);
        ReflectionTestUtils.setField(repo, "status", OssRepoStatus.SUSPENDED);
        ossRepoRepository.save(repo);
    }

    private List<Long> idsByStars(Long categoryId, String language, String pattern, Integer afterStars,
                                  Long afterId) {
        return ossRepoRepository.findPageSortedByStars(
                        OssRepoStatus.ACTIVE, categoryId, language, pattern, afterStars, afterId, PAGE).stream()
                .map(OssRepo::getId)
                .toList();
    }

    private List<Long> idsByName(Long categoryId, String language, String pattern, String afterKey,
                                 Long afterId) {
        return ossRepoRepository.findPageSortedByName(
                        OssRepoStatus.ACTIVE, categoryId, language, pattern, afterKey, afterId, PAGE).stream()
                .map(OssRepo::getId)
                .toList();
    }
}
