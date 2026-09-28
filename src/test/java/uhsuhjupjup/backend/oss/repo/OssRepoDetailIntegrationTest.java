package uhsuhjupjup.backend.oss.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.nio.charset.StandardCharsets;
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
class OssRepoDetailIntegrationTest {

    private static final String URL = "/api/oss/repos/{repoId}";
    private static final String EXPLORE_URL = "/api/oss/repos";

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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private long nextGithubId = 1;

    @BeforeEach
    void setUp() {
        ossRepoRepository.deleteAllInBatch();
    }

    @Test
    void detail_withoutLogin_returnsRepoWithCategoriesInCatalogOrderAndGithubUrl() throws Exception {
        Long id = save("vercel/next.js", "The React Framework", "JavaScript", 130_000, "backend", "web-frontend");

        mockMvc.perform(get(URL, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.fullName").value("vercel/next.js"))
                .andExpect(jsonPath("$.description").value("The React Framework"))
                .andExpect(jsonPath("$.primaryLanguage").value("JavaScript"))
                .andExpect(jsonPath("$.stars").value(130_000))
                .andExpect(jsonPath("$.categories[*].code", contains("web-frontend", "backend")))
                .andExpect(jsonPath("$.categories[0].nameKo").value("웹 프론트엔드"))
                .andExpect(jsonPath("$.categories[0].nameEn").value("Web Frontend"))
                .andExpect(jsonPath("$.categories[1].nameKo").value("백엔드와 API"))
                .andExpect(jsonPath("$.categories[1].nameEn").value("Backend & APIs"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/vercel/next.js"));
    }

    @Test
    void detail_returnsOnlyPublicFields() throws Exception {
        Long id = save("spring-projects/spring-boot", "Spring Boot", "Java", 80_000, "backend");

        assertThat(body(get(URL, id), status().isOk())).containsOnlyKeys(
                "id", "fullName", "description", "primaryLanguage", "stars", "categories", "githubUrl");
    }

    @Test
    void detail_repoWithoutDescriptionLanguageOrCategories_returnsNullsAndEmptyCategories() throws Exception {
        Long id = save("octocat/Hello-World", null, null, 80);

        mockMvc.perform(get(URL, id))
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
    void detail_categories_matchTheSameRepoInExplore() throws Exception {
        Long id = save("flutter/flutter", null, "Dart", 170_000, "desktop", "mobile");

        String detail = content(get(URL, id));
        String explore = content(get(EXPLORE_URL));

        List<String> codes = JsonPath.read(detail, "$.categories[*].code");
        List<Map<String, Object>> fromDetail = JsonPath.read(detail, "$.categories");
        List<Map<String, Object>> fromExplore = JsonPath.read(explore, "$.items[0].categories");
        assertThat(codes).containsExactly("mobile", "desktop");
        assertThat(fromDetail).isEqualTo(fromExplore);
    }

    @Test
    void detail_unknownRepo_returns404RepoNotFound() throws Exception {
        Long id = save("spring-projects/spring-boot", null, "Java", 80_000);

        mockMvc.perform(get(URL, id + 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_REPO_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("레포를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$", not(hasKey("fieldErrors"))));
    }

    @Test
    void detail_negativeRepoId_returns404RepoNotFound() throws Exception {
        mockMvc.perform(get(URL, -1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_REPO_NOT_FOUND"));
    }

    @Test
    void detail_suspendedRepo_returnsSame404AsUnknownRepo() throws Exception {
        Long suspended = save("octocat/suspended", "Spring web backend", "Java", 90_000, "backend");
        suspend(suspended);

        Map<String, Object> suspendedBody = body(get(URL, suspended), status().isNotFound());
        Map<String, Object> unknownBody = body(get(URL, suspended + 1), status().isNotFound());

        assertThat(withoutPathAndTimestamp(suspendedBody))
                .containsEntry("code", "OSS_REPO_NOT_FOUND")
                .isEqualTo(withoutPathAndTimestamp(unknownBody));
    }

    @Test
    void detail_repoIdNotANumber_returns400WithRepoIdFieldError() throws Exception {
        mockMvc.perform(get(URL, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/oss/repos/abc"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("repoId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));
    }

    @Test
    void detail_trailingSlashWithoutRepoId_returns404NotFoundInsteadOfExplore() throws Exception {
        save("spring-projects/spring-boot", null, "Java", 80_000);

        mockMvc.perform(get(EXPLORE_URL + "/"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$", not(hasKey("items"))));
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

    private void suspend(Long repoId) {
        jdbcTemplate.update("UPDATE oss_repo SET status = 'SUSPENDED' WHERE id = ?", repoId);
    }

    private String content(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Map<String, Object> body(MockHttpServletRequestBuilder request, ResultMatcher expectedStatus)
            throws Exception {
        String content = mockMvc.perform(request)
                .andExpect(expectedStatus)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readerForMapOf(Object.class).readValue(content);
    }

    private static Map<String, Object> withoutPathAndTimestamp(Map<String, Object> body) {
        body.remove("path");
        body.remove("timestamp");
        return body;
    }
}
