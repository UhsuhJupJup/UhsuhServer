package uhsuhjupjup.backend.oss.issue.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.common.exception.GlobalExceptionHandler;
import uhsuhjupjup.backend.oss.issue.application.OssIssueService;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueCursor;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDifficultyFilter;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssuePageResult;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueResult;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OssRepoIssueControllerTest {

    private static final String URL = "/api/oss/repos/{repoId}/issues";
    private static final Long REPO_ID = 10L;
    private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";
    private static final String NUMBER_REQUIRED = "숫자여야 합니다.";
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 5, 9, 12, 44);
    private static final LocalDateTime GRADED_AT = LocalDateTime.of(2026, 10, 8, 14, 20, 11);
    private static final OssIssuePageResult EMPTY = new OssIssuePageResult(OssIssueLanguage.KO, List.of(), null);

    @Mock
    private OssIssueService ossIssueService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OssRepoIssueController(ossIssueService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
                .build();
    }

    @Test
    void repoIssues_withoutParameters_returnsLangItemsWithGithubUrlAndEncodedCursor() throws Exception {
        OssIssueCursor next = new OssIssueCursor(OPENED_AT.minusDays(2), 498L);
        given(ossIssueService.getRepoIssues(REPO_ID, null, null, null, null)).willReturn(new OssIssuePageResult(
                OssIssueLanguage.KO,
                List.of(item(501L, 1284, OPENED_AT, "설정 순서 때문에 재시도 간격이 무시된다."),
                        item(498L, 1279, OPENED_AT.minusDays(2), "종료 뒤 워커가 남는다.")),
                next));

        mockMvc.perform(get(URL, REPO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("ko"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(501))
                .andExpect(jsonPath("$.items[0].number").value(1284))
                .andExpect(jsonPath("$.items[0].title").value("Issue 1284"))
                .andExpect(jsonPath("$.items[0].githubUrl").value("https://github.com/acme/fastqueue/issues/1284"))
                .andExpect(jsonPath("$.items[0].githubCreatedAt").value("2026-10-05T09:12:44"))
                .andExpect(jsonPath("$.items[0].difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.items[0].evidence.problem").value("PRESENT"))
                .andExpect(jsonPath("$.items[0].evidence.reproduction").value("PARTIAL"))
                .andExpect(jsonPath("$.items[0].evidence.cause").value("ABSENT"))
                .andExpect(jsonPath("$.items[0].evidence.fixDirection").value("PRESENT"))
                .andExpect(jsonPath("$.items[0].evidence.relatedPr").value(true))
                .andExpect(jsonPath("$.items[0].summary").value("설정 순서 때문에 재시도 간격이 무시된다."))
                .andExpect(jsonPath("$.items[0].gradedAt").value("2026-10-08T14:20:11"))
                .andExpect(jsonPath("$.items[1].id").value(498))
                .andExpect(jsonPath("$.items[1].githubUrl").value("https://github.com/acme/fastqueue/issues/1279"))
                .andExpect(jsonPath("$.items[1].githubCreatedAt").value("2026-10-03T09:12:44"))
                .andExpect(jsonPath("$.nextCursor").value(next.encode()));
    }

    @Test
    void repoIssues_returnsOnlyPublicFieldsWithoutReasonModelOrHashes() throws Exception {
        given(ossIssueService.getRepoIssues(REPO_ID, null, null, null, null)).willReturn(new OssIssuePageResult(
                OssIssueLanguage.KO, List.of(item(501L, 1284, OPENED_AT, "요약")), null));

        Map<String, Object> body = body(get(URL, REPO_ID));

        assertThat(body).containsOnlyKeys("lang", "items", "nextCursor");
        assertThat(body.get("items")).asInstanceOf(LIST).singleElement().asInstanceOf(MAP)
                .containsOnlyKeys("id", "number", "title", "githubUrl", "githubCreatedAt", "difficulty", "evidence",
                        "summary", "gradedAt")
                .extractingByKey("evidence").asInstanceOf(MAP)
                .containsOnlyKeys("problem", "reproduction", "cause", "fixDirection", "relatedPr");
    }

    @Test
    void repoIssues_lastPage_returnsNullCursorExplicitly() throws Exception {
        given(ossIssueService.getRepoIssues(REPO_ID, null, null, null, null)).willReturn(EMPTY);

        mockMvc.perform(get(URL, REPO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("ko"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$", hasKey("nextCursor")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "easy | easy",
            "medium | medium",
            "hard | hard",
            "easy,medium | easy,medium",
            "hard,easy | easy,hard",
            "easy,easy | easy",
            "medium,hard,easy,hard | easy,medium,hard"})
    void repoIssues_difficultyCodesSeparatedByCommas_areBoundAsASet(String difficulty, String expected)
            throws Exception {
        given(ossIssueService.getRepoIssues(REPO_ID, OssIssueDifficultyFilter.fromCodes(expected), null, null, null))
                .willReturn(EMPTY);

        mockMvc.perform(get(URL, REPO_ID).param("difficulty", difficulty))
                .andExpect(status().isOk());
    }

    @Test
    void repoIssues_repeatedDifficulty_meansTheSameAsCommas() throws Exception {
        given(ossIssueService.getRepoIssues(REPO_ID, OssIssueDifficultyFilter.fromCodes("easy,hard"), null, null,
                null)).willReturn(EMPTY);

        mockMvc.perform(get(URL, REPO_ID).param("difficulty", "hard", "easy", "hard"))
                .andExpect(status().isOk());

        then(ossIssueService).should()
                .getRepoIssues(REPO_ID, OssIssueDifficultyFilter.fromCodes("easy,hard"), null, null, null);
    }

    @Test
    void repoIssues_emptyDifficultyLangCursorAndSize_areTreatedAsAbsent() throws Exception {
        given(ossIssueService.getRepoIssues(REPO_ID, null, null, null, null)).willReturn(EMPTY);

        mockMvc.perform(get(URL, REPO_ID)
                        .param("difficulty", "")
                        .param("lang", "")
                        .param("cursor", "")
                        .param("size", ""))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"EASY", "Easy", "MEDIUM", " easy", "easy ", " ", "easy, medium", "easy ,medium",
            "normal", "easy,normal", "easy;medium", "easy medium", "1"})
    void repoIssues_difficultyOutsideTheThreeLowercaseCodes_returns400WithDifficultyFieldError(String difficulty)
            throws Exception {
        assertBadRequest(get(URL, REPO_ID).param("difficulty", difficulty), "difficulty", INVALID_FORMAT);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"easy,", ",easy", ",", ",,", "easy,,medium", "easy,medium,"})
    void repoIssues_emptyCodeAmongCommas_returns400WithDifficultyFieldError(String difficulty) throws Exception {
        assertBadRequest(get(URL, REPO_ID).param("difficulty", difficulty), "difficulty", INVALID_FORMAT);
    }

    @Test
    void repoIssues_repeatedDifficultyWithAnEmptyValue_returns400WithDifficultyFieldError() throws Exception {
        assertBadRequest(get(URL, REPO_ID).param("difficulty", "easy", ""), "difficulty", INVALID_FORMAT);
        assertBadRequest(get(URL, REPO_ID).param("difficulty", "", ""), "difficulty", INVALID_FORMAT);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"ko, KO", "en, EN"})
    void repoIssues_langParameter_isBoundAndEchoed(String lang, OssIssueLanguage expected) throws Exception {
        given(ossIssueService.getRepoIssues(REPO_ID, null, expected, null, null))
                .willReturn(new OssIssuePageResult(expected, List.of(), null));

        mockMvc.perform(get(URL, REPO_ID).param("lang", lang))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value(lang));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"KO", "En", " ", " ko", "en ", "jp", "kor", "ko-KR", "ko_KR", "ko,en"})
    void repoIssues_langOutsideTwoLowercaseCodes_returns400WithLangFieldError(String lang) throws Exception {
        assertBadRequest(get(URL, REPO_ID).param("lang", lang), "lang", INVALID_FORMAT);
    }

    @Test
    void repoIssues_repeatedLang_returns400WithLangFieldError() throws Exception {
        assertBadRequest(get(URL, REPO_ID).param("lang", "ko", "en"), "lang", INVALID_FORMAT);
    }

    @Test
    void repoIssues_cursor_isDecodedBeforeReachingTheService() throws Exception {
        OssIssueCursor cursor = new OssIssueCursor(OPENED_AT, 498L);
        given(ossIssueService.getRepoIssues(REPO_ID, OssIssueDifficultyFilter.fromCodes("easy"), OssIssueLanguage.EN,
                cursor, 5)).willReturn(EMPTY);

        mockMvc.perform(get(URL, REPO_ID)
                        .param("difficulty", "easy")
                        .param("lang", "en")
                        .param("cursor", cursor.encode())
                        .param("size", "5"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"!!!", "a", "NDk4OjIw MjYtMTAtMDM", "NDk4OjIwMjYtMTAtMDN+", "U1RBUlM6MTA6ODAwMDA"})
    void repoIssues_undecodableCursor_returns400WithCursorFieldError(String cursor) throws Exception {
        assertBadRequest(get(URL, REPO_ID).param("cursor", cursor), "cursor", INVALID_FORMAT);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"498", "0:2026-10-05T09:12:44", "498:2026-10-05", "498:2026-10-05T09:12:44Z",
            "498:2026-10-05T09:12:44.5", "498:2026-10-05T09:12:44.000"})
    void repoIssues_cursorOfValidBase64ButUnknownLayout_returns400WithCursorFieldError(String payload)
            throws Exception {
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertBadRequest(get(URL, REPO_ID).param("cursor", cursor), "cursor", INVALID_FORMAT);
    }

    @ParameterizedTest(name = "[{index}] size {0}")
    @ValueSource(ints = {0, -3, 1, 50, 1000})
    void repoIssues_sizeOfAnyInt_isPassedAsReceivedForTheServiceToClamp(int size) throws Exception {
        given(ossIssueService.getRepoIssues(REPO_ID, null, null, null, size)).willReturn(EMPTY);

        mockMvc.perform(get(URL, REPO_ID).param("size", String.valueOf(size)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"twenty", "1.5", "3000000000"})
    void repoIssues_sizeNotAnInt_returns400WithSizeFieldError(String size) throws Exception {
        assertBadRequest(get(URL, REPO_ID).param("size", size), "size", NUMBER_REQUIRED);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
    void repoIssues_repoIdNotALong_returns400WithRepoIdFieldError(String repoId) throws Exception {
        assertBadRequest(get("/api/oss/repos/" + repoId + "/issues"), "repoId", NUMBER_REQUIRED);
    }

    @Test
    void repoIssues_severalBadValues_reportsOnlyTheFirstInParameterOrder() throws Exception {
        assertBadRequest(get("/api/oss/repos/abc/issues").param("difficulty", "EASY"), "repoId", NUMBER_REQUIRED);
        assertBadRequest(get(URL, REPO_ID).param("difficulty", "EASY").param("lang", "KO"), "difficulty",
                INVALID_FORMAT);
        assertBadRequest(get(URL, REPO_ID).param("lang", "KO").param("cursor", "!!!"), "lang", INVALID_FORMAT);
        assertBadRequest(get(URL, REPO_ID).param("cursor", "!!!").param("size", "many"), "cursor", INVALID_FORMAT);
    }

    @Test
    void repoIssues_repoNotFound_returns404RepoNotFound() throws Exception {
        given(ossIssueService.getRepoIssues(99L, null, OssIssueLanguage.EN, null, null))
                .willThrow(new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND));

        mockMvc.perform(get(URL, 99).param("lang", "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_REPO_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("레포를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/oss/repos/99/issues"));
    }

    private void assertBadRequest(RequestBuilder request, String field, String reason) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(reason));

        then(ossIssueService).shouldHaveNoInteractions();
    }

    private Map<String, Object> body(RequestBuilder request) throws Exception {
        String content = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readerForMapOf(Object.class).readValue(content);
    }

    private static OssIssueResult item(Long id, int number, LocalDateTime openedAt, String summary) {
        return new OssIssueResult(id, number, "Issue " + number, openedAt, "acme/fastqueue",
                OssIssueDifficulty.MEDIUM, OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL, OssIssueEvidence.ABSENT,
                OssIssueEvidence.PRESENT, true, summary, GRADED_AT);
    }
}
