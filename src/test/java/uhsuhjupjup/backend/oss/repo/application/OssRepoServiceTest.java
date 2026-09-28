package uhsuhjupjup.backend.oss.repo.application;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.repo.application.dto.OssCategoryResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoCursor;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoPageResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoSort;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OssRepoServiceTest {

    private static final OssRepoStatus ACTIVE = OssRepoStatus.ACTIVE;

    @Mock
    private OssRepoRepository ossRepoRepository;

    @Mock
    private OssCategoryRepository ossCategoryRepository;

    @Mock
    private OssRepoCategoryRepository ossRepoCategoryRepository;

    @InjectMocks
    private OssRepoService ossRepoService;

    private final OssCategory aiMl = category(1L, "ai-ml", "AI와 머신러닝", "AI & Machine Learning");
    private final OssCategory backend = category(4L, "backend", "백엔드와 API", "Backend & APIs");
    private final OssCategory devtools = category(8L, "devtools", "개발 도구", "Developer Tools");

    private final OssRepo react = repo(2L, "facebook/react", "JavaScript", 230_000);
    private final OssRepo springBoot = repo(1L, "spring-projects/spring-boot", "Java", 80_000);
    private final OssRepo flask = repo(3L, "pallets/flask", "Python", 70_000);

    @Test
    void explore_withoutParameters_readsActiveReposByStarsWithDefaultSize() {
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, null, null, null, null, null, Limit.of(21)))
                .willReturn(List.of(react, springBoot));
        given(ossRepoCategoryRepository.findWithCategoryByRepoIdIn(List.of(2L, 1L)))
                .willReturn(springBoot.linkCategories(List.of(backend, devtools)));

        OssRepoPageResult result = ossRepoService.explore(null, null, null, null, null, null);

        assertThat(result.items()).containsExactly(
                new OssRepoResult(2L, 2L, "facebook/react", null, "JavaScript", 230_000, ACTIVE, List.of()),
                new OssRepoResult(1L, 1L, "spring-projects/spring-boot", null, "Java", 80_000, ACTIVE, List.of(
                        new OssCategoryResult("backend", "백엔드와 API", "Backend & APIs"),
                        new OssCategoryResult("devtools", "개발 도구", "Developer Tools"))));
        assertThat(result.nextCursor()).isNull();
        then(ossCategoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void explore_moreThanPageSize_returnsPageAndCursorAfterItsLastRepo() {
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, null, null, null, null, null, Limit.of(3)))
                .willReturn(List.of(react, springBoot, flask));
        given(ossRepoCategoryRepository.findWithCategoryByRepoIdIn(List.of(2L, 1L))).willReturn(List.of());

        OssRepoPageResult result = ossRepoService.explore(null, null, null, null, null, 2);

        assertThat(result.items()).extracting(OssRepoResult::id).containsExactly(2L, 1L);
        assertThat(result.nextCursor()).isEqualTo(new OssRepoCursor(OssRepoSort.STARS, "80000", 1L));
    }

    @Test
    void explore_exactlyPageSize_returnsNoCursor() {
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, null, null, null, null, null, Limit.of(3)))
                .willReturn(List.of(react, springBoot));
        given(ossRepoCategoryRepository.findWithCategoryByRepoIdIn(List.of(2L, 1L))).willReturn(List.of());

        OssRepoPageResult result = ossRepoService.explore(null, null, null, null, null, 2);

        assertThat(result.items()).hasSize(2);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void explore_nothingFound_returnsEmptyPageWithoutReadingCategories() {
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, null, null, null, null, null, Limit.of(21)))
                .willReturn(List.of());

        OssRepoPageResult result = ossRepoService.explore(null, null, null, null, null, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        then(ossRepoCategoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void explore_sortByName_readsByNameAndReturnsNameCursor() {
        given(ossRepoRepository.findPageSortedByName(ACTIVE, null, null, null, null, null, Limit.of(2)))
                .willReturn(List.of(react, flask));
        given(ossRepoCategoryRepository.findWithCategoryByRepoIdIn(List.of(2L))).willReturn(List.of());

        OssRepoPageResult result = ossRepoService.explore(null, null, null, OssRepoSort.NAME, null, 1);

        assertThat(result.items()).extracting(OssRepoResult::id).containsExactly(2L);
        assertThat(result.nextCursor()).isEqualTo(new OssRepoCursor(OssRepoSort.NAME, "facebook/react", 2L));
    }

    @Test
    void explore_starsCursor_readsAfterItsStarsAndId() {
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, null, null, null, 80_000, 1L, Limit.of(21)))
                .willReturn(List.of(flask));
        given(ossRepoCategoryRepository.findWithCategoryByRepoIdIn(List.of(3L))).willReturn(List.of());

        OssRepoPageResult result = ossRepoService.explore(
                null, null, null, OssRepoSort.STARS, new OssRepoCursor(OssRepoSort.STARS, "80000", 1L), null);

        assertThat(result.items()).extracting(OssRepoResult::id).containsExactly(3L);
    }

    @Test
    void explore_nameCursor_readsAfterItsNameAndId() {
        given(ossRepoRepository.findPageSortedByName(ACTIVE, null, null, null, "facebook/react", 2L, Limit.of(21)))
                .willReturn(List.of(flask));
        given(ossRepoCategoryRepository.findWithCategoryByRepoIdIn(List.of(3L))).willReturn(List.of());

        OssRepoPageResult result = ossRepoService.explore(
                null, null, null, OssRepoSort.NAME, new OssRepoCursor(OssRepoSort.NAME, "facebook/react", 2L), null);

        assertThat(result.items()).extracting(OssRepoResult::id).containsExactly(3L);
    }

    @Test
    void explore_nameCursorWithStarsSort_throwsValidationErrorWithoutReading() {
        assertFailsWith(ErrorCode.VALIDATION_ERROR, () -> ossRepoService.explore(null, null, null,
                OssRepoSort.STARS, new OssRepoCursor(OssRepoSort.NAME, "facebook/react", 2L), null));

        thenNothingWasRead();
    }

    @Test
    void explore_nameCursorWithoutSort_isCheckedAgainstDefaultStarsSort() {
        assertFailsWith(ErrorCode.VALIDATION_ERROR, () -> ossRepoService.explore(null, null, null,
                null, new OssRepoCursor(OssRepoSort.NAME, "facebook/react", 2L), null));

        thenNothingWasRead();
    }

    @Test
    void explore_starsCursorWithNameSortAndUnknownCategory_reportsCursorMismatchFirst() {
        assertFailsWith(ErrorCode.VALIDATION_ERROR, () -> ossRepoService.explore("robotics", null, null,
                OssRepoSort.NAME, new OssRepoCursor(OssRepoSort.STARS, "80000", 1L), null));

        thenNothingWasRead();
    }

    @ParameterizedTest(name = "[{index}] size {0} -> {1}")
    @CsvSource(value = {"null, 20", "0, 20", "-5, 20", "1, 1", "20, 20", "50, 50", "51, 50", "1000, 50"},
            nullValues = "null")
    void explore_size_isClampedBetweenOneAndFifty(Integer size, int expected) {
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, null, null, null, null, null, Limit.of(expected + 1)))
                .willReturn(List.of());

        ossRepoService.explore(null, null, null, null, null, size);

        then(ossRepoRepository).should()
                .findPageSortedByStars(ACTIVE, null, null, null, null, null, Limit.of(expected + 1));
    }

    @Test
    void explore_categoryCode_filtersByThatCategoryId() {
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("backend"))).willReturn(List.of(backend));
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, 4L, null, null, null, null, Limit.of(21)))
                .willReturn(List.of(springBoot));
        given(ossRepoCategoryRepository.findWithCategoryByRepoIdIn(List.of(1L)))
                .willReturn(springBoot.linkCategories(List.of(backend)));

        OssRepoPageResult result = ossRepoService.explore("backend", null, null, null, null, null);

        assertThat(result.items()).extracting(OssRepoResult::id).containsExactly(1L);
    }

    @Test
    void explore_categoryCodeMatchingOnlyIgnoringCase_throwsCategoryNotFound() {
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("Backend"))).willReturn(List.of(backend));

        assertFailsWith(ErrorCode.OSS_CATEGORY_NOT_FOUND,
                () -> ossRepoService.explore("Backend", null, null, null, null, null));

        then(ossRepoRepository).shouldHaveNoInteractions();
    }

    @Test
    void explore_unknownCategoryCode_throwsCategoryNotFound() {
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("robotics"))).willReturn(List.of());

        assertFailsWith(ErrorCode.OSS_CATEGORY_NOT_FOUND,
                () -> ossRepoService.explore("robotics", null, null, null, null, null));

        then(ossRepoRepository).shouldHaveNoInteractions();
    }

    @Test
    void explore_filters_areStrippedBeforeUse() {
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("backend"))).willReturn(List.of(backend));
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, 4L, "Java", "%spring boot%", null, null, Limit.of(21)))
                .willReturn(List.of());

        ossRepoService.explore(" backend\t", "  Java ", "\n spring boot  ", null, null, null);

        then(ossRepoRepository).should()
                .findPageSortedByStars(ACTIVE, 4L, "Java", "%spring boot%", null, null, Limit.of(21));
    }

    @Test
    void explore_blankFilters_areNotApplied() {
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, null, null, null, null, null, Limit.of(21)))
                .willReturn(List.of());

        ossRepoService.explore("  ", "", " \t ", null, null, null);

        then(ossRepoRepository).should().findPageSortedByStars(ACTIVE, null, null, null, null, null, Limit.of(21));
        then(ossCategoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void explore_query_escapesLikeWildcardsAndEscapeCharacter() {
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, null, null, "%50!%!_off!!\\now%", null, null,
                Limit.of(21))).willReturn(List.of());

        ossRepoService.explore(null, null, "50%_off!\\now", null, null, null);

        then(ossRepoRepository).should()
                .findPageSortedByStars(ACTIVE, null, null, "%50!%!_off!!\\now%", null, null, Limit.of(21));
    }

    @Test
    void explore_categoriesOfEachRepo_keepQueryOrderAndMissingOnesAreEmpty() {
        List<OssRepoCategory> links = new ArrayList<>();
        links.add(flask.linkCategories(List.of(aiMl)).get(0));
        links.addAll(springBoot.linkCategories(List.of(backend, devtools)));
        given(ossRepoRepository.findPageSortedByStars(ACTIVE, null, null, null, null, null, Limit.of(21)))
                .willReturn(List.of(react, springBoot, flask));
        given(ossRepoCategoryRepository.findWithCategoryByRepoIdIn(List.of(2L, 1L, 3L))).willReturn(links);

        OssRepoPageResult result = ossRepoService.explore(null, null, null, null, null, null);

        assertThat(result.items()).extracting(OssRepoResult::fullName).containsExactly(
                "facebook/react", "spring-projects/spring-boot", "pallets/flask");
        assertThat(result.items().get(0).categories()).isEmpty();
        assertThat(result.items().get(1).categories()).extracting(OssCategoryResult::code)
                .containsExactly("backend", "devtools");
        assertThat(result.items().get(2).categories()).extracting(OssCategoryResult::code)
                .containsExactly("ai-ml");
    }

    private void thenNothingWasRead() {
        then(ossRepoRepository).shouldHaveNoInteractions();
        then(ossCategoryRepository).shouldHaveNoInteractions();
        then(ossRepoCategoryRepository).shouldHaveNoInteractions();
    }

    private static void assertFailsWith(ErrorCode expected, ThrowingCallable explore) {
        assertThatThrownBy(explore)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    private static OssRepo repo(Long id, String fullName, String language, int stars) {
        OssRepo repo = OssRepo.create(id, fullName, null, language, stars);
        ReflectionTestUtils.setField(repo, "id", id);
        return repo;
    }

    private static OssCategory category(long id, String code, String nameKo, String nameEn) {
        OssCategory category = OssCategory.create(code, nameKo, nameEn);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
