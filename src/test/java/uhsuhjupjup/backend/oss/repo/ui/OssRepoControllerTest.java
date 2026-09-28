package uhsuhjupjup.backend.oss.repo.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.common.exception.GlobalExceptionHandler;
import uhsuhjupjup.backend.oss.repo.application.OssRepoService;
import uhsuhjupjup.backend.oss.repo.application.dto.OssCategoryResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoCursor;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoPageResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoSort;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OssRepoControllerTest {

    private static final String URL = "/api/oss/repos";
    private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";
    private static final OssRepoPageResult EMPTY = new OssRepoPageResult(List.of(), null);

    @Mock
    private OssRepoService ossRepoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OssRepoController(ossRepoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void explore_withoutParameters_returnsPublicFieldsAndEncodedCursor() throws Exception {
        given(ossRepoService.explore(null, null, null, null, null, null)).willReturn(new OssRepoPageResult(
                List.of(new OssRepoResult(10L, 1296269L, "spring-projects/spring-boot", "Spring Boot", "Java",
                        80_000, OssRepoStatus.ACTIVE, List.of(
                        new OssCategoryResult("backend", "백엔드와 API", "Backend & APIs"),
                        new OssCategoryResult("devtools", "개발 도구", "Developer Tools")))),
                new OssRepoCursor(OssRepoSort.STARS, "80000", 10L)));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].fullName").value("spring-projects/spring-boot"))
                .andExpect(jsonPath("$.items[0].description").value("Spring Boot"))
                .andExpect(jsonPath("$.items[0].primaryLanguage").value("Java"))
                .andExpect(jsonPath("$.items[0].stars").value(80_000))
                .andExpect(jsonPath("$.items[0].categories.length()").value(2))
                .andExpect(jsonPath("$.items[0].categories[0].code").value("backend"))
                .andExpect(jsonPath("$.items[0].categories[0].nameKo").value("백엔드와 API"))
                .andExpect(jsonPath("$.items[0].categories[0].nameEn").value("Backend & APIs"))
                .andExpect(jsonPath("$.items[0].categories[1].code").value("devtools"))
                .andExpect(jsonPath("$.items[0].githubId").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").value("U1RBUlM6MTA6ODAwMDA"));
    }

    @Test
    void explore_lastPage_returnsNullCursorExplicitly() throws Exception {
        given(ossRepoService.explore(null, null, null, null, null, null)).willReturn(EMPTY);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$", hasKey("nextCursor")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void explore_filtersAndSize_arePassedAsReceived() throws Exception {
        given(ossRepoService.explore(" backend", "Java", "spring boot", null, null, 5)).willReturn(EMPTY);

        mockMvc.perform(get(URL)
                        .param("category", " backend")
                        .param("language", "Java")
                        .param("q", "spring boot")
                        .param("size", "5"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"stars, STARS", "name, NAME"})
    void explore_sortParameter_isBoundToSort(String parameter, OssRepoSort expected) throws Exception {
        given(ossRepoService.explore(null, null, null, expected, null, null)).willReturn(EMPTY);

        mockMvc.perform(get(URL).param("sort", parameter))
                .andExpect(status().isOk());
    }

    @Test
    void explore_emptySortCursorAndSize_areTreatedAsAbsent() throws Exception {
        given(ossRepoService.explore(null, null, null, null, null, null)).willReturn(EMPTY);

        mockMvc.perform(get(URL).param("sort", "").param("cursor", "").param("size", ""))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"STARS", "Name", " stars", "stars ", "popular", "stars,name"})
    void explore_sortOutsideTwoLowercaseValues_returns400WithSortFieldError(String sort) throws Exception {
        mockMvc.perform(get(URL).param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(INVALID_FORMAT));

        then(ossRepoService).shouldHaveNoInteractions();
    }

    @Test
    void explore_repeatedSort_returns400WithSortFieldError() throws Exception {
        mockMvc.perform(get(URL).param("sort", "stars", "name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));

        then(ossRepoService).shouldHaveNoInteractions();
    }

    @Test
    void explore_cursor_isDecodedBeforeReachingService() throws Exception {
        OssRepoCursor cursor = new OssRepoCursor(OssRepoSort.NAME, "facebook/react", 25L);
        given(ossRepoService.explore(null, null, null, OssRepoSort.NAME, cursor, null)).willReturn(EMPTY);

        mockMvc.perform(get(URL).param("sort", "name").param("cursor", cursor.encode()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"!!!", "U1RBUlM6MTA", "c3RhcnM6MTA6ODAwMDA", "TkFNRToxMDo"})
    void explore_undecodableCursor_returns400WithCursorFieldError(String cursor) throws Exception {
        mockMvc.perform(get(URL).param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cursor"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(INVALID_FORMAT));

        then(ossRepoService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"twenty", "3000000000"})
    void explore_sizeNotAnInt_returns400WithSizeFieldError(String size) throws Exception {
        mockMvc.perform(get(URL).param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));

        then(ossRepoService).shouldHaveNoInteractions();
    }

    @Test
    void explore_cursorOfValidBase64ButUnknownLayout_returns400() throws Exception {
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get(URL).param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cursor"));
    }

    @Test
    void detail_returnsPublicFieldsCategoriesAndGithubUrl() throws Exception {
        given(ossRepoService.getDetail(10L)).willReturn(new OssRepoResult(10L, 1296269L,
                "spring-projects/spring-boot", "Spring Boot", "Java", 80_000, OssRepoStatus.ACTIVE, List.of(
                new OssCategoryResult("backend", "백엔드와 API", "Backend & APIs"),
                new OssCategoryResult("devtools", "개발 도구", "Developer Tools"))));

        mockMvc.perform(get(URL + "/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.fullName").value("spring-projects/spring-boot"))
                .andExpect(jsonPath("$.description").value("Spring Boot"))
                .andExpect(jsonPath("$.primaryLanguage").value("Java"))
                .andExpect(jsonPath("$.stars").value(80_000))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].code").value("backend"))
                .andExpect(jsonPath("$.categories[0].nameKo").value("백엔드와 API"))
                .andExpect(jsonPath("$.categories[0].nameEn").value("Backend & APIs"))
                .andExpect(jsonPath("$.categories[1].code").value("devtools"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/spring-projects/spring-boot"))
                .andExpect(jsonPath("$.githubId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void detail_repoWithoutOptionalValues_returnsNullsEmptyCategoriesAndGithubUrlOfStoredName() throws Exception {
        given(ossRepoService.getDetail(11L)).willReturn(new OssRepoResult(11L, 1L, "octocat/Hello-World", null,
                null, 80, OssRepoStatus.ACTIVE, List.of()));

        mockMvc.perform(get(URL + "/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("description")))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$", hasKey("primaryLanguage")))
                .andExpect(jsonPath("$.primaryLanguage").value(nullValue()))
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/octocat/Hello-World"));
    }

    @Test
    void detail_repoNotFound_returns404RepoNotFound() throws Exception {
        given(ossRepoService.getDetail(99L)).willThrow(new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND));

        mockMvc.perform(get(URL + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_REPO_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("레포를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/api/oss/repos/99"));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
    void detail_repoIdNotALong_returns400WithRepoIdFieldError(String repoId) throws Exception {
        mockMvc.perform(get(URL + "/" + repoId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("repoId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));

        then(ossRepoService).shouldHaveNoInteractions();
    }
}
