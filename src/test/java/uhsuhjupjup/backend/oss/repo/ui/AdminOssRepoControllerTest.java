package uhsuhjupjup.backend.oss.repo.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.common.exception.GlobalExceptionHandler;
import uhsuhjupjup.backend.oss.repo.application.OssRepoRegistrationService;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.support.AdminMemberStubResolver;
import uhsuhjupjup.backend.support.MemberFixture;

import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOssRepoControllerTest {

    private static final String REGISTER_URL = "/api/admin/oss/repos";
    private static final String FORMAT_REASON = "GitHub 레포 이름(owner/name) 형식이어야 합니다.";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private OssRepoRegistrationService ossRepoRegistrationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOssRepoController(ossRepoRegistrationService))
                .setCustomArgumentResolvers(new AdminMemberStubResolver(MemberFixture.member(1L, "admin@example.com")))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_validFullName_returns201WithSavedRepo() throws Exception {
        given(ossRepoRegistrationService.register("octocat/Hello-World")).willReturn(new OssRepoResult(
                10L, 1296269L, "octocat/Hello-World", "My first repository on GitHub!", "Java", 80,
                OssRepoStatus.ACTIVE));

        register("octocat/Hello-World")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.githubId").value(1296269))
                .andExpect(jsonPath("$.fullName").value("octocat/Hello-World"))
                .andExpect(jsonPath("$.description").value("My first repository on GitHub!"))
                .andExpect(jsonPath("$.primaryLanguage").value("Java"))
                .andExpect(jsonPath("$.stars").value(80))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void register_repoWithoutDescriptionAndLanguage_returnsThemAsNull() throws Exception {
        given(ossRepoRegistrationService.register("octocat/empty")).willReturn(new OssRepoResult(
                11L, 7L, "octocat/empty", null, null, 0, OssRepoStatus.ACTIVE));

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

    private ResultActions register(String fullName) throws Exception {
        return mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Map.of("fullName", fullName))));
    }

    private static OssRepoResult savedRepo() {
        return new OssRepoResult(10L, 1296269L, "octocat/Hello-World", null, "Java", 80, OssRepoStatus.ACTIVE);
    }
}
