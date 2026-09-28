package uhsuhjupjup.backend.oss.repo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class OssRepoExploreIntegrationTest {

    private static final String URL = "/api/oss/repos";
    private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";
    private static final int MAX_PAGES = 100;

    private static final String REACT = "facebook/react";
    private static final String SPRING_BOOT = "spring-projects/spring-boot";
    private static final String FLASK = "pallets/flask";
    private static final String KAFKA = "apache/kafka";
    private static final String HELLO_WORLD = "octocat/Hello-World";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private OssCategoryRepository ossCategoryRepository;

    @Autowired
    private OssRepoCategoryRepository ossRepoCategoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private long nextGithubId = 1;

    @BeforeEach
    void setUp() {
        ossRepoRepository.deleteAllInBatch();
    }

    @Test
    void explore_withoutLogin_listsActiveReposByStarsWithCategoriesInCatalogOrder() throws Exception {
        saveCatalog();

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].fullName", contains(REACT, SPRING_BOOT, FLASK, KAFKA, HELLO_WORLD)))
                .andExpect(jsonPath("$.items[1].description").value("Spring Boot helps you create Spring-powered apps"))
                .andExpect(jsonPath("$.items[1].primaryLanguage").value("Java"))
                .andExpect(jsonPath("$.items[1].stars").value(80_000))
                .andExpect(jsonPath("$.items[1].categories[*].code", contains("backend", "devtools")))
                .andExpect(jsonPath("$.items[1].categories[0].nameKo").value("백엔드와 API"))
                .andExpect(jsonPath("$.items[1].categories[0].nameEn").value("Backend & APIs"))
                .andExpect(jsonPath("$.items[1].categories[1].nameKo").value("개발 도구"))
                .andExpect(jsonPath("$.items[1].categories[1].nameEn").value("Developer Tools"))
                .andExpect(jsonPath("$.items[4].description").value(nullValue()))
                .andExpect(jsonPath("$.items[4].primaryLanguage").value(nullValue()))
                .andExpect(jsonPath("$.items[4].categories").isArray())
                .andExpect(jsonPath("$.items[4].categories").isEmpty())
                .andExpect(jsonPath("$.items[*].githubId").isEmpty())
                .andExpect(jsonPath("$.items[*].status").isEmpty())
                .andExpect(jsonPath("$", hasKey("nextCursor")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void explore_itemId_isTheStoredRepoId() throws Exception {
        Long id = save(SPRING_BOOT, null, "Java", 80_000);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
    }

    @ParameterizedTest(name = "[{index}] category={0}, language={1}, q={2}, sort={3}")
    @MethodSource("filters")
    void explore_filters_narrowDownWithAnd(String category, String language, String q, String sort,
                                           List<String> expected) throws Exception {
        saveCatalog();

        Page page = page(withParams(get(URL), params(category, language, q, sort)));

        assertThat(page.fullNames()).containsExactlyElementsOf(expected);
        assertThat(page.nextCursor()).isNull();
    }

    static Stream<Arguments> filters() {
        return Stream.of(
                Arguments.of("backend", null, null, null, List.of(SPRING_BOOT, FLASK)),
                Arguments.of("web-frontend", null, null, null, List.of(REACT)),
                Arguments.of(null, "Java", null, null, List.of(SPRING_BOOT, KAFKA)),
                Arguments.of(null, "java", null, null, List.of(SPRING_BOOT, KAFKA)),
                Arguments.of(null, null, "spring", null, List.of(SPRING_BOOT)),
                Arguments.of(null, null, "WEB", null, List.of(REACT, FLASK)),
                Arguments.of(null, null, "micro framework", null, List.of(FLASK)),
                Arguments.of(null, null, "facebook", null, List.of(REACT)),
                Arguments.of("backend", "Java", null, null, List.of(SPRING_BOOT)),
                Arguments.of("backend", null, "web", null, List.of(FLASK)),
                Arguments.of(null, "Java", "kafka", null, List.of(KAFKA)),
                Arguments.of("web-frontend", "JavaScript", "native", null, List.of(REACT)),
                Arguments.of("backend", null, null, "name", List.of(FLASK, SPRING_BOOT)),
                Arguments.of("backend", "JavaScript", null, null, List.of()),
                Arguments.of("data", null, "spring", null, List.of()));
    }

    @ParameterizedTest(name = "[{index}] q={0}")
    @CsvSource(delimiter = '|', value = {
            "100%       | octocat/percent",
            "%          | octocat/percent",
            "snake_case | octocat/snake",
            "_          | octocat/snake",
            "o!         | octocat/bang",
            "!          | octocat/bang",
            "\\tools    | octocat/windows"})
    void explore_queryWildcardsAndEscapeCharacter_areMatchedLiterally(String q, String expected)
            throws Exception {
        saveWith("octocat/percent", "100% open source");
        saveWith("octocat/thousand", "1000 stars");
        saveWith("octocat/snake", "snake case helper named snake_case");
        saveWith("octocat/snakex", "snakeXcase helper");
        saveWith("octocat/bang", "Hello! world");
        saveWith("octocat/plain", "Hello world");
        saveWith("octocat/windows", "C:\\tools\\path");
        saveWith("octocat/slash", "C:/tools/path");

        assertThat(page(get(URL).param("q", q)).fullNames()).containsExactly(expected);
    }

    @Test
    void explore_blankFilters_areNotApplied() throws Exception {
        saveCatalog();

        Page page = page(get(URL).param("category", "  ").param("language", "").param("q", " \t "));

        assertThat(page.fullNames()).containsExactly(REACT, SPRING_BOOT, FLASK, KAFKA, HELLO_WORLD);
    }

    @Test
    void explore_filtersWithSurroundingSpaces_areStripped() throws Exception {
        saveCatalog();

        Page page = page(get(URL).param("category", " backend ").param("language", " Python").param("q", " flask "));

        assertThat(page.fullNames()).containsExactly(FLASK);
    }

    @ParameterizedTest(name = "[{index}] category={0}")
    @ValueSource(strings = {"Backend", "BACKEND", "robotics"})
    void explore_categoryCodeNotMatchingExactly_returns400CategoryNotFound(String category) throws Exception {
        saveCatalog();

        mockMvc.perform(get(URL).param("category", category))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OSS_CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("없는 카테고리 코드가 있습니다."));
    }

    @Test
    void explore_sortByName_ordersByFullNameIgnoringCase() throws Exception {
        saveNamed("zeta/app", "Beta/lib", "delta/api", "alpha/tool", "Gamma/core", "Epsilon/ui");
        suspend(save("aaa/suspended", null, "Java", 1));

        assertThat(page(get(URL).param("sort", "name")).fullNames()).containsExactly(
                "alpha/tool", "Beta/lib", "delta/api", "Epsilon/ui", "Gamma/core", "zeta/app");
    }

    @ParameterizedTest(name = "[{index}] size={0}")
    @ValueSource(ints = {1, 2, 3, 6, 7, 50})
    void explore_walkingStarsCursor_visitsEveryRepoOnceEvenWhenTiesCrossPages(int size) throws Exception {
        Long top = save("walk/top", null, "Java", 500);
        Long tieA = save("walk/tie-a", null, "Java", 300);
        Long tieB = save("walk/tie-b", null, "Java", 300);
        Long tieC = save("walk/tie-c", null, "Java", 300);
        Long tieD = save("walk/tie-d", null, "Java", 300);
        Long lowA = save("walk/low-a", null, "Java", 100);
        Long lowB = save("walk/low-b", null, "Java", 100);
        suspend(save("walk/suspended", null, "Java", 300));

        List<List<Long>> pages = walk(size, Map.of());

        assertWalk(pages, size, List.of(top, tieD, tieC, tieB, tieA, lowB, lowA));
    }

    @ParameterizedTest(name = "[{index}] size={0}")
    @ValueSource(ints = {1, 2, 4, 6})
    void explore_walkingNameCursor_visitsEveryRepoOnceInNameOrder(int size) throws Exception {
        List<Long> ids = saveNamed("zeta/app", "Beta/lib", "delta/api", "alpha/tool", "Gamma/core", "Epsilon/ui");
        suspend(save("aaa/suspended", null, "Java", 1));

        List<List<Long>> pages = walk(size, Map.of("sort", "name"));

        assertWalk(pages, size, List.of(ids.get(3), ids.get(1), ids.get(2), ids.get(5), ids.get(4), ids.get(0)));
    }

    @Test
    void explore_walkingStarsCursorWithLanguage_keepsFilterOnEveryPage() throws Exception {
        Long javaTieA = save("walk/java-tie-a", null, "Java", 300);
        save("walk/python-tie", null, "Python", 300);
        Long javaTieB = save("walk/java-tie-b", null, "Java", 300);
        Long javaTieC = save("walk/java-tie-c", null, "Java", 300);
        save("walk/python-low", null, "Python", 100);
        Long javaLow = save("walk/java-low", null, "Java", 100);

        List<List<Long>> pages = walk(2, Map.of("language", "Java"));

        assertWalk(pages, 2, List.of(javaTieC, javaTieB, javaTieA, javaLow));
    }

    @Test
    void explore_walkingNameCursorWithCategory_keepsCategoryOnEveryPage() throws Exception {
        Long zeta = save("zeta/app", null, "Java", 1, "backend");
        save("beta/lib", null, "Java", 1, "data");
        Long delta = save("delta/api", null, "Java", 1, "backend", "devtools");
        Long alpha = save("alpha/tool", null, "Java", 1, "backend");

        List<List<Long>> pages = walk(2, Map.of("sort", "name", "category", "backend"));

        assertWalk(pages, 2, List.of(alpha, delta, zeta));
    }

    @ParameterizedTest(name = "[{index}] size={0} -> {1}")
    @CsvSource(value = {"null, 20", "'', 20", "0, 20", "-3, 20", "1, 1", "50, 50", "51, 50", "1000, 50"},
            nullValues = "null")
    void explore_size_isClampedAndNeverRejected(String size, int expected) throws Exception {
        for (int i = 0; i < 51; i++) {
            save("octocat/repo-" + i, null, "Java", i);
        }
        MockHttpServletRequestBuilder request = get(URL);
        if (size != null) {
            request.param("size", size);
        }

        Page page = page(request);

        assertThat(page.ids()).hasSize(expected);
        assertThat(page.nextCursor()).isNotNull();
    }

    @ParameterizedTest(name = "[{index}] sort={0}")
    @ValueSource(strings = {"popular", "STARS", "Name"})
    void explore_unknownSort_returns400WithSortFieldError(String sort) throws Exception {
        mockMvc.perform(get(URL).param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(INVALID_FORMAT));
    }

    @Test
    void explore_undecodableCursor_returns400WithCursorFieldError() throws Exception {
        String notOurLayout = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("hello".getBytes(StandardCharsets.UTF_8));

        for (String cursor : List.of("!!!", notOurLayout)) {
            mockMvc.perform(get(URL).param("cursor", cursor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("cursor"))
                    .andExpect(jsonPath("$.fieldErrors[0].reason").value(INVALID_FORMAT));
        }
    }

    @Test
    void explore_cursorFromOtherSort_returns400() throws Exception {
        saveCatalog();
        String nameCursor = page(get(URL).param("sort", "name").param("size", "1")).nextCursor();
        String starsCursor = page(get(URL).param("size", "1")).nextCursor();

        List<MockHttpServletRequestBuilder> mismatches = List.of(
                get(URL).param("sort", "stars").param("cursor", nameCursor),
                get(URL).param("cursor", nameCursor),
                get(URL).param("sort", "name").param("cursor", starsCursor));
        for (MockHttpServletRequestBuilder mismatch : mismatches) {
            mockMvc.perform(mismatch)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                    .andExpect(jsonPath("$", not(hasKey("fieldErrors"))));
        }
    }

    @Test
    void explore_requestValueErrors_areReportedBeforeUnknownCategory() throws Exception {
        saveCatalog();
        String nameCursor = page(get(URL).param("sort", "name").param("size", "1")).nextCursor();

        mockMvc.perform(get(URL).param("sort", "popular").param("category", "robotics"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get(URL).param("cursor", nameCursor).param("category", "robotics"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void explore_nonNumericSize_returns400WithSizeFieldError() throws Exception {
        mockMvc.perform(get(URL).param("size", "twenty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));
    }

    private void saveCatalog() {
        save(REACT, "The library for web and native user interfaces.", "JavaScript", 230_000, "web-frontend");
        save(SPRING_BOOT, "Spring Boot helps you create Spring-powered apps", "Java", 80_000, "devtools", "backend");
        save(FLASK, "The Python micro framework for building web applications.", "Python", 70_000, "backend");
        save(KAFKA, "Mirror of Apache Kafka", "Java", 30_000, "data");
        save(HELLO_WORLD, null, null, 80);
        suspend(save("suspended/java-backend", "Spring web backend", "Java", 90_000, "backend"));
    }

    private Long save(String fullName, String description, String language, int stars, String... categoryCodes) {
        OssRepo repo = ossRepoRepository.save(
                OssRepo.create(nextGithubId++, fullName, description, language, stars));
        List<OssCategory> categories = Stream.of(categoryCodes)
                .map(code -> ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of(code)).get(0))
                .toList();
        ossRepoCategoryRepository.saveAll(repo.linkCategories(categories));
        return repo.getId();
    }

    private void saveWith(String fullName, String description) {
        save(fullName, description, null, 0);
    }

    private List<Long> saveNamed(String... fullNames) {
        return Stream.of(fullNames)
                .map(fullName -> save(fullName, null, "Java", 1))
                .toList();
    }

    private void suspend(Long repoId) {
        jdbcTemplate.update("UPDATE oss_repo SET status = 'SUSPENDED' WHERE id = ?", repoId);
    }

    private static Map<String, String> params(String category, String language, String q, String sort) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "category", category);
        putIfPresent(params, "language", language);
        putIfPresent(params, "q", q);
        putIfPresent(params, "sort", sort);
        return params;
    }

    private static void putIfPresent(Map<String, String> params, String name, String value) {
        if (value != null) {
            params.put(name, value);
        }
    }

    private static MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder request,
                                                            Map<String, String> params) {
        params.forEach(request::param);
        return request;
    }

    private List<List<Long>> walk(int size, Map<String, String> params) throws Exception {
        List<List<Long>> pages = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = withParams(get(URL), params).param("size", String.valueOf(size));
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            Page page = page(request);
            pages.add(page.ids());
            cursor = page.nextCursor();
        } while (cursor != null && pages.size() < MAX_PAGES);
        return pages;
    }

    private static void assertWalk(List<List<Long>> pages, int size, List<Long> expectedOrder) {
        assertThat(pages.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(expectedOrder);
        assertThat(pages).hasSize((expectedOrder.size() + size - 1) / size);
        assertThat(pages.subList(0, pages.size() - 1)).allSatisfy(page -> assertThat(page).hasSize(size));
    }

    private Page page(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> fullNames = JsonPath.read(body, "$.items[*].fullName");
        List<Number> ids = JsonPath.read(body, "$.items[*].id");
        String nextCursor = JsonPath.read(body, "$.nextCursor");
        return new Page(fullNames, ids.stream().map(Number::longValue).toList(), nextCursor);
    }

    private record Page(List<String> fullNames, List<Long> ids, String nextCursor) {
    }
}
