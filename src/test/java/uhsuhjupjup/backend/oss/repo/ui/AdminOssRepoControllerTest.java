package uhsuhjupjup.backend.oss.repo.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.common.exception.GlobalExceptionHandler;
import uhsuhjupjup.backend.oss.repo.application.OssRepoCategoryService;
import uhsuhjupjup.backend.oss.repo.application.OssRepoRegistrationService;
import uhsuhjupjup.backend.oss.repo.application.dto.OssCategoryResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.support.AdminMemberStubResolver;
import uhsuhjupjup.backend.support.MemberFixture;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOssRepoControllerTest {

    private static final String REGISTER_URL = "/api/admin/oss/repos";
    private static final String UPDATE_URL = "/api/admin/oss/repos/10";
    private static final String FORMAT_REASON = "GitHub 레포 이름(owner/name) 형식이어야 합니다.";
    private static final String LIMIT_REASON = "최대 2개까지 지정할 수 있습니다.";
    private static final String BLANK_CODE_REASON = "빈 코드는 넣을 수 없습니다.";
    private static final OssCategoryResult AI_ML = new OssCategoryResult("ai-ml", "AI와 머신러닝", "AI & Machine Learning");
    private static final OssCategoryResult BACKEND = new OssCategoryResult("backend", "백엔드와 API", "Backend & APIs");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private OssRepoRegistrationService ossRepoRegistrationService;

    @Mock
    private OssRepoCategoryService ossRepoCategoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminOssRepoController(ossRepoRegistrationService, ossRepoCategoryService))
                .setCustomArgumentResolvers(new AdminMemberStubResolver(MemberFixture.member(1L, "admin@example.com")))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_validFullName_returns201WithSavedRepo() throws Exception {
        given(ossRepoRegistrationService.register("octocat/Hello-World")).willReturn(new OssRepoResult(
                10L, 1296269L, "octocat/Hello-World", "My first repository on GitHub!", "Java", 80,
                OssRepoStatus.ACTIVE, List.of()));

        register("octocat/Hello-World")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.githubId").value(1296269))
                .andExpect(jsonPath("$.fullName").value("octocat/Hello-World"))
                .andExpect(jsonPath("$.description").value("My first repository on GitHub!"))
                .andExpect(jsonPath("$.primaryLanguage").value("Java"))
                .andExpect(jsonPath("$.stars").value(80))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.categories").isEmpty());
    }

    @Test
    void register_repoWithoutDescriptionAndLanguage_returnsThemAsNull() throws Exception {
        given(ossRepoRegistrationService.register("octocat/empty")).willReturn(new OssRepoResult(
                11L, 7L, "octocat/empty", null, null, 0, OssRepoStatus.ACTIVE, List.of()));

        register("octocat/empty")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.primaryLanguage").value(nullValue()));
    }

    @Test
    void register_surroundingWhitespace_isStrippedBeforeValidation() throws Exception {
        given(ossRepoRegistrationService.register("octocat/Hello-World")).willReturn(savedRepo());

        register("  octocat/Hello-World \n").andExpect(status().isCreated());

        then(ossRepoRegistrationService).should().register("octocat/Hello-World");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("namesFollowingGitHubRules")
    void register_nameFollowingGitHubRules_reachesService(String fullName) throws Exception {
        given(ossRepoRegistrationService.register(fullName)).willReturn(savedRepo());

        register(fullName).andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @MethodSource("namesBreakingGitHubRules")
    void register_nameBreakingGitHubRules_returns400WithoutCallingService(String fullName) throws Exception {
        register(fullName)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fullName"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(FORMAT_REASON));

        then(ossRepoRegistrationService).shouldHaveNoInteractions();
    }

    @Test
    void register_missingFullName_returns400AsRequired() throws Exception {
        mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fullName"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("필수 값입니다."));

        then(ossRepoRegistrationService).shouldHaveNoInteractions();
    }

    @Test
    void update_validCodes_returns200WithRepoAndCategories() throws Exception {
        given(ossRepoCategoryService.replaceCategories(10L, List.of("backend", "ai-ml"))).willReturn(new OssRepoResult(
                10L, 1296269L, "octocat/Hello-World", "My first repository on GitHub!", "Java", 80,
                OssRepoStatus.ACTIVE, List.of(AI_ML, BACKEND)));

        update("{\"categoryCodes\":[\"backend\",\"ai-ml\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.githubId").value(1296269))
                .andExpect(jsonPath("$.fullName").value("octocat/Hello-World"))
                .andExpect(jsonPath("$.description").value("My first repository on GitHub!"))
                .andExpect(jsonPath("$.primaryLanguage").value("Java"))
                .andExpect(jsonPath("$.stars").value(80))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].code").value("ai-ml"))
                .andExpect(jsonPath("$.categories[0].nameKo").value("AI와 머신러닝"))
                .andExpect(jsonPath("$.categories[0].nameEn").value("AI & Machine Learning"))
                .andExpect(jsonPath("$.categories[1].code").value("backend"))
                .andExpect(jsonPath("$.categories[1].nameKo").value("백엔드와 API"))
                .andExpect(jsonPath("$.categories[1].nameEn").value("Backend & APIs"))
                .andExpect(jsonPath("$.categories[*].id").doesNotExist());
    }

    @Test
    void update_repeatedCodes_areCountedOnceInFirstSeenOrder() throws Exception {
        given(ossRepoCategoryService.replaceCategories(10L, List.of("ai-ml", "backend")))
                .willReturn(repoWith(AI_ML, BACKEND));

        update("{\"categoryCodes\":[\"ai-ml\",\"ai-ml\",\"backend\",\"backend\"]}").andExpect(status().isOk());

        then(ossRepoCategoryService).should().replaceCategories(10L, List.of("ai-ml", "backend"));
    }

    @Test
    void update_emptyList_reachesServiceToClearCategories() throws Exception {
        given(ossRepoCategoryService.replaceCategories(10L, List.of())).willReturn(repoWith());

        update("{\"categoryCodes\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.categories").isEmpty());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "{\"categoryCodes\":[\"ai-ml\",\"backend\",\"devtools\"]}",
            "{\"categoryCodes\":[\"ai-ml\",\"backend\",\"ai-ml\",\"devtools\"]}"
    })
    void update_moreThanTwoDistinctCodes_returns400WithoutCallingService(String body) throws Exception {
        update(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryCodes"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(LIMIT_REASON));

        then(ossRepoCategoryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"{}", "{\"categoryCodes\":null}"})
    void update_missingCodes_returns400AsRequired(String body) throws Exception {
        update(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryCodes"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("필수 값입니다."));

        then(ossRepoCategoryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @MethodSource("bodiesWithBlankOrNullCode")
    void update_blankOrNullCode_returns400WithoutCallingService(String body, String field) throws Exception {
        update(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(BLANK_CODE_REASON));

        then(ossRepoCategoryService).shouldHaveNoInteractions();
    }

    static Stream<String> namesFollowingGitHubRules() {
        return Stream.of(
                "a/b",
                "spring-projects/spring-boot",
                "Octo-Cat-42/Hello_World.js",
                "octocat/.github",
                "octocat/...",
                "a".repeat(39) + "/" + "b".repeat(100));
    }

    static Stream<String> namesBreakingGitHubRules() {
        return Stream.of(
                "",
                "   ",
                "octocat",
                "/Hello-World",
                "octocat/",
                "octocat/Hello-World/issues",
                "-octocat/Hello-World",
                "octocat-/Hello-World",
                "octo--cat/Hello-World",
                "octo_cat/Hello-World",
                "octo.cat/Hello-World",
                "octocat/.",
                "octocat/..",
                "octocat/%2e%2e",
                "octocat/Hello World",
                "octocat/Hello-World?tab=issues",
                "https://github.com/octocat/Hello-World",
                "a".repeat(40) + "/Hello-World",
                "octocat/" + "b".repeat(101));
    }

    static Stream<Arguments> bodiesWithBlankOrNullCode() {
        return Stream.of(
                Arguments.of("{\"categoryCodes\":[\"\"]}", "categoryCodes[0]"),
                Arguments.of("{\"categoryCodes\":[\"   \"]}", "categoryCodes[0]"),
                Arguments.of("{\"categoryCodes\":[null]}", "categoryCodes[0]"),
                Arguments.of("{\"categoryCodes\":[\"ai-ml\",\"\"]}", "categoryCodes[1]"),
                Arguments.of("{\"categoryCodes\":[\"ai-ml\",null,null]}", "categoryCodes[1]"));
    }

    private ResultActions register(String fullName) throws Exception {
        return mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Map.of("fullName", fullName))));
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(patch(UPDATE_URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static OssRepoResult savedRepo() {
        return new OssRepoResult(10L, 1296269L, "octocat/Hello-World", null, "Java", 80, OssRepoStatus.ACTIVE,
                List.of());
    }

    private static OssRepoResult repoWith(OssCategoryResult... categories) {
        return new OssRepoResult(10L, 1296269L, "octocat/Hello-World", null, "Java", 80, OssRepoStatus.ACTIVE,
                List.of(categories));
    }
}
