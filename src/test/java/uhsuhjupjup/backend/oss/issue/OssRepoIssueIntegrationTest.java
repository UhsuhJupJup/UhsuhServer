package uhsuhjupjup.backend.oss.issue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
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
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueDetail;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueLookup;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueBodyHash;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGrader;
import uhsuhjupjup.backend.oss.pipeline.grading.application.OssIssueGradeSaver;
import uhsuhjupjup.backend.oss.pipeline.grading.application.OssIssueGradingService;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class OssRepoIssueIntegrationTest {

    private static final String URL = "/api/oss/repos/{repoId}/issues";
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 7, 12, 0, 0);
    private static final String BODY = "Steps to reproduce\n1. run it";
    private static final String EDITED_BODY = "Steps to reproduce\n1. run it twice";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final int MAX_PAGES = 30;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OssIssueGradingService ossIssueGradingService;

    @Autowired
    private OssIssueGradeSaver ossIssueGradeSaver;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private OssIssueRepository ossIssueRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockitoBean
    private GitHubClient gitHubClient;

    @MockitoBean
    private IssueGrader issueGrader;

    private long nextGithubId = 1;

    @BeforeEach
    void setUp() {
        ossRepoRepository.deleteAllInBatch();
    }

    @Test
    void repoIssues_listsOnlyIssuesWhoseCurrentGradeIsNotExcludedWithoutLoginInKorean() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue medium = saveIssue(repo, 1284, OPENED_AT);
        OssIssue spam = saveIssue(repo, 1285, OPENED_AT.plusHours(1));
        OssIssue ungradable = saveIssue(repo, 1286, OPENED_AT.plusHours(2));
        OssIssue bodyChanged = saveIssue(repo, 1287, OPENED_AT.plusHours(3));
        OssIssue easy = saveIssue(repo, 1288, OPENED_AT.minusHours(1));
        gradeByRun(repo, Map.of(
                medium, verdict(OssIssueDifficulty.MEDIUM, medium),
                spam, excluded(OssIssueGradeExclusion.SPAM),
                bodyChanged, verdict(OssIssueDifficulty.EASY, bodyChanged),
                easy, verdict(OssIssueDifficulty.EASY, easy)));
        changeStoredBody(bodyChanged, EDITED_BODY);

        mockMvc.perform(get(URL, repo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("ko"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(medium.getId()))
                .andExpect(jsonPath("$.items[0].number").value(1284))
                .andExpect(jsonPath("$.items[0].title").value("Stored title 1284"))
                .andExpect(jsonPath("$.items[0].githubUrl").value("https://github.com/acme/fastqueue/issues/1284"))
                .andExpect(jsonPath("$.items[0].githubCreatedAt").value("2026-10-07T12:00:00"))
                .andExpect(jsonPath("$.items[0].difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.items[0].evidence.problem").value("PRESENT"))
                .andExpect(jsonPath("$.items[0].evidence.reproduction").value("PRESENT"))
                .andExpect(jsonPath("$.items[0].evidence.cause").value("ABSENT"))
                .andExpect(jsonPath("$.items[0].evidence.fixDirection").value("PARTIAL"))
                .andExpect(jsonPath("$.items[0].evidence.relatedPr").value(false))
                .andExpect(jsonPath("$.items[0].summary").value("이슈 1284 요약"))
                .andExpect(jsonPath("$.items[0].gradedAt").value(latestGradedAt(medium)))
                .andExpect(jsonPath("$.items[1].id").value(easy.getId()))
                .andExpect(jsonPath("$.items[1].difficulty").value("EASY"))
                .andExpect(jsonPath("$", hasKey("nextCursor")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        assertThat(ossIssueRepository.findById(ungradable.getId()).orElseThrow().getGradingFailures()).isEqualTo(1);
    }

    @Test
    void repoIssues_returnsOnlyPublicFields() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1, OPENED_AT);
        gradeByRun(repo, Map.of(issue, verdict(OssIssueDifficulty.MEDIUM, issue)));

        Map<String, Object> body = body(get(URL, repo.getId()), status().isOk());

        assertThat(body).containsOnlyKeys("lang", "items", "nextCursor");
        assertThat(body.get("items")).asInstanceOf(LIST).singleElement().asInstanceOf(MAP)
                .containsOnlyKeys("id", "number", "title", "githubUrl", "githubCreatedAt", "difficulty", "evidence",
                        "summary", "gradedAt")
                .extractingByKey("evidence").asInstanceOf(MAP)
                .containsOnlyKeys("problem", "reproduction", "cause", "fixDirection", "relatedPr");
    }

    @Test
    void repoIssues_difficultyFilter_keepsOnlyTheChosenDifficulties() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue easy = saveIssue(repo, 1, OPENED_AT.plusHours(2));
        OssIssue medium = saveIssue(repo, 2, OPENED_AT.plusHours(1));
        OssIssue hard = saveIssue(repo, 3, OPENED_AT);
        gradeByRun(repo, Map.of(
                easy, verdict(OssIssueDifficulty.EASY, easy),
                medium, verdict(OssIssueDifficulty.MEDIUM, medium),
                hard, verdict(OssIssueDifficulty.HARD, hard)));

        assertThat(listedIds(get(URL, repo.getId()).param("difficulty", "easy"))).containsExactly(easy.getId());
        assertThat(listedIds(get(URL, repo.getId()).param("difficulty", "hard,easy")))
                .containsExactly(easy.getId(), hard.getId());
        assertThat(listedIds(get(URL, repo.getId()).param("difficulty", "hard", "medium")))
                .containsExactly(medium.getId(), hard.getId());
        assertThat(listedIds(get(URL, repo.getId()).param("difficulty", "medium,medium")))
                .containsExactly(medium.getId());
        assertThat(listedIds(get(URL, repo.getId())))
                .containsExactly(easy.getId(), medium.getId(), hard.getId());
    }

    @Test
    void repoIssues_walkingWithASmallSize_returnsEveryListedIssueOnceNewestFirst() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        Map<OssIssue, OssIssueVerdict> verdicts = new LinkedHashMap<>();
        OssIssue newest = gradedLater(verdicts, repo, 1, OPENED_AT.plusHours(3), OssIssueDifficulty.EASY);
        OssIssue spam = saveIssue(repo, 2, OPENED_AT.plusMinutes(150));
        verdicts.put(spam, excluded(OssIssueGradeExclusion.SPAM));
        OssIssue second = gradedLater(verdicts, repo, 3, OPENED_AT.plusHours(2), OssIssueDifficulty.MEDIUM);
        OssIssue ungradable = saveIssue(repo, 4, OPENED_AT.plusHours(1));
        OssIssue tiedLowId = gradedLater(verdicts, repo, 5, OPENED_AT, OssIssueDifficulty.MEDIUM);
        OssIssue tiedQuestion = saveIssue(repo, 6, OPENED_AT);
        verdicts.put(tiedQuestion, excluded(OssIssueGradeExclusion.QUESTION));
        OssIssue tiedMiddleId = gradedLater(verdicts, repo, 7, OPENED_AT, OssIssueDifficulty.HARD);
        OssIssue tiedHighId = gradedLater(verdicts, repo, 8, OPENED_AT, OssIssueDifficulty.MEDIUM);
        OssIssue older = gradedLater(verdicts, repo, 9, OPENED_AT.minusHours(1), OssIssueDifficulty.EASY);
        OssIssue oldest = gradedLater(verdicts, repo, 10, OPENED_AT.minusDays(2), OssIssueDifficulty.MEDIUM);
        gradeByRun(repo, verdicts);

        List<Long> everyPageOfTwo = walk(repo, "2", null);
        List<Long> everyMediumOneByOne = walk(repo, "1", "medium");

        assertThat(everyPageOfTwo).containsExactly(newest.getId(), second.getId(), tiedHighId.getId(),
                tiedMiddleId.getId(), tiedLowId.getId(), older.getId(), oldest.getId());
        assertThat(everyPageOfTwo).doesNotContain(spam.getId(), ungradable.getId(), tiedQuestion.getId());
        assertThat(everyPageOfTwo).isEqualTo(listedIds(get(URL, repo.getId()).param("size", "50")));
        assertThat(everyMediumOneByOne).containsExactly(second.getId(), tiedHighId.getId(), tiedLowId.getId(),
                oldest.getId());
    }

    @Test
    void repoIssues_afterTheBodyChanges_dropsTheIssueUntilTheNewBodyIsGraded() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue changing = saveIssue(repo, 1, OPENED_AT);
        OssIssue steady = saveIssue(repo, 2, OPENED_AT.minusHours(1));
        gradeByRun(repo, Map.of(
                changing, verdict(OssIssueDifficulty.MEDIUM, changing),
                steady, verdict(OssIssueDifficulty.HARD, steady)));
        assertThat(listedIds(get(URL, repo.getId()))).containsExactly(changing.getId(), steady.getId());

        changeStoredBody(changing, EDITED_BODY);

        assertThat(listedIds(get(URL, repo.getId()))).containsExactly(steady.getId());

        given(gitHubClient.findIssue(repo.getGithubId(), changing.getNumber()))
                .willReturn(found(changing, EDITED_BODY));
        given(issueGrader.grade(eq(fetchedTitle(changing)), any(), any()))
                .willReturn(new IssueGradingResult(verdict(OssIssueDifficulty.EASY, changing), MODEL));
        assertThat(ossIssueGradingService.gradeRepo(repo.getId()).graded()).isEqualTo(1);

        mockMvc.perform(get(URL, repo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(changing.getId()))
                .andExpect(jsonPath("$.items[0].difficulty").value("EASY"))
                .andExpect(jsonPath("$.items[0].gradedAt").value(latestGradedAt(changing)));
    }

    @Test
    void repoIssues_newerExcludedGradeOfTheSameBody_hidesTheIssueAndANewerVisibleOneShowsItAgain()
            throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1, OPENED_AT);
        gradeByRun(repo, Map.of(issue, verdict(OssIssueDifficulty.MEDIUM, issue)));

        ossIssueGradeSaver.save(issue, excluded(OssIssueGradeExclusion.DUPLICATE), MODEL, issue.getBodyHash());

        assertThat(listedIds(get(URL, repo.getId()))).isEmpty();

        ossIssueGradeSaver.save(issue, verdict(OssIssueDifficulty.HARD, issue), MODEL, issue.getBodyHash());

        mockMvc.perform(get(URL, repo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].difficulty").value("HARD"));
    }

    @Test
    void repoIssues_inEnglish_changesOnlyLangAndSummaries() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue first = saveIssue(repo, 1284, OPENED_AT);
        OssIssue second = saveIssue(repo, 1285, OPENED_AT.minusHours(1));
        gradeByRun(repo, Map.of(
                first, verdict(OssIssueDifficulty.MEDIUM, first),
                second, verdict(OssIssueDifficulty.EASY, second)));

        Map<String, Object> korean = body(get(URL, repo.getId()).param("lang", "ko"), status().isOk());
        Map<String, Object> english = body(get(URL, repo.getId()).param("lang", "en"), status().isOk());

        assertThat(korean).containsEntry("lang", "ko");
        assertThat(english).containsEntry("lang", "en");
        assertThat(summaries(korean)).containsExactly("이슈 1284 요약", "이슈 1285 요약");
        assertThat(summaries(english)).containsExactly("Summary of issue 1284", "Summary of issue 1285");
        assertThat(withoutLanguage(english)).isEqualTo(withoutLanguage(korean));
    }

    @Test
    void repoIssues_ofSuspendedOrUnknownRepo_returnTheSame404() throws Exception {
        OssRepo suspended = saveRepo("acme/suspended");
        OssIssue issue = saveIssue(suspended, 1, OPENED_AT);
        gradeByRun(suspended, Map.of(issue, verdict(OssIssueDifficulty.MEDIUM, issue)));
        assertThat(listedIds(get(URL, suspended.getId()))).containsExactly(issue.getId());
        suspend(suspended);

        Map<String, Object> ofSuspended = notFoundBody(suspended.getId());
        Map<String, Object> ofUnknown = notFoundBody(suspended.getId() + 1);

        assertThat(ofSuspended)
                .containsEntry("code", "OSS_REPO_NOT_FOUND")
                .containsEntry("message", "레포를 찾을 수 없습니다.")
                .containsEntry("status", 404)
                .doesNotContainKey("fieldErrors");
        assertThat(ofUnknown).isEqualTo(ofSuspended);
    }

    @Test
    void repoIssues_activeRepoWithoutCollectedIssues_returnsAnEmptyPage() throws Exception {
        OssRepo repo = saveRepo("acme/quiet");

        mockMvc.perform(get(URL, repo.getId()).param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("en"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$", hasKey("nextCursor")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void repoIssues_badParameter_returns400EvenForAnUnknownRepo() throws Exception {
        mockMvc.perform(get(URL, 999_999).param("difficulty", "easy,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/oss/repos/999999/issues"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("difficulty"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("형식이 올바르지 않습니다."));
    }

    @Test
    void apiDocs_listUnderTheIssueTagTakeCommaSeparatedDifficultiesAndShareTheEvidenceSchemaWithTheDetail()
            throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        DocumentContext openApi = JsonPath.parse(docs);
        List<Object> operationTags = openApi.read("$.paths['/api/oss/repos/{repoId}/issues'].get.tags");
        List<Object> issueTags = openApi.read("$.tags[?(@.name == '오픈소스 - 이슈')]");
        List<Object> difficultyParameters = openApi.read(
                "$.paths['/api/oss/repos/{repoId}/issues'].get.parameters[?(@.name == 'difficulty')]");

        assertThat(operationTags).containsExactly("오픈소스 - 이슈");
        assertThat(issueTags).hasSize(1);
        assertThat(difficultyParameters).singleElement().asInstanceOf(MAP)
                .containsEntry("explode", false)
                .extractingByKey("schema").asInstanceOf(MAP)
                .containsEntry("type", "array")
                .extractingByKey("items").asInstanceOf(MAP)
                .extractingByKey("enum").asInstanceOf(LIST)
                .containsExactly("easy", "medium", "hard");
        assertThat(openApi.read("$.components.schemas.OssIssueResponse.properties.evidence['$ref']", String.class))
                .isEqualTo("#/components/schemas/Evidence")
                .isEqualTo(openApi.read("$.components.schemas.OssIssueDetailResponse.properties.evidence['$ref']",
                        String.class));
    }

    private OssIssue gradedLater(Map<OssIssue, OssIssueVerdict> verdicts, OssRepo repo, int number,
                                 LocalDateTime openedAt, OssIssueDifficulty difficulty) {
        OssIssue issue = saveIssue(repo, number, openedAt);
        verdicts.put(issue, verdict(difficulty, issue));
        return issue;
    }

    private void gradeByRun(OssRepo repo, Map<OssIssue, OssIssueVerdict> verdicts) {
        given(gitHubClient.findIssue(anyLong(), anyInt())).willReturn(GitHubIssueLookup.gone());
        verdicts.forEach((issue, verdict) -> {
            given(gitHubClient.findIssue(repo.getGithubId(), issue.getNumber())).willReturn(found(issue, BODY));
            given(issueGrader.grade(eq(fetchedTitle(issue)), any(), any()))
                    .willReturn(new IssueGradingResult(verdict, MODEL));
        });

        assertThat(ossIssueGradingService.gradeRepo(repo.getId()).graded()).isEqualTo(verdicts.size());
    }

    private static GitHubIssueLookup found(OssIssue issue, String body) {
        return GitHubIssueLookup.found(new GitHubIssueDetail(issue.getGithubIssueId(), fetchedTitle(issue), body,
                List.of("bug"), true, false, 0, "octocat", "User"));
    }

    private static String fetchedTitle(OssIssue issue) {
        return "Fetched title " + issue.getNumber();
    }

    private static OssIssueVerdict verdict(OssIssueDifficulty difficulty, OssIssue issue) {
        String number = String.valueOf(issue.getNumber());
        return switch (difficulty) {
            case EASY -> new OssIssueVerdict(difficulty, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT,
                    OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, true, null,
                    "원인과 수정 방향까지 있다.", "The cause and the fix are given.",
                    "이슈 " + number + " 요약", "Summary of issue " + number);
            case MEDIUM -> new OssIssueVerdict(difficulty, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT,
                    OssIssueEvidence.ABSENT, OssIssueEvidence.PARTIAL, false, null,
                    "재현 절차는 있지만 원인이 없다.", "Steps are given but the cause is missing.",
                    "이슈 " + number + " 요약", "Summary of issue " + number);
            case HARD -> new OssIssueVerdict(difficulty, OssIssueEvidence.PRESENT, OssIssueEvidence.ABSENT,
                    OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, false, null,
                    "문제만 있고 재현과 원인이 없다.", "Only the problem is described.",
                    "이슈 " + number + " 요약", "Summary of issue " + number);
        };
    }

    private static OssIssueVerdict excluded(OssIssueGradeExclusion exclusion) {
        return new OssIssueVerdict(OssIssueDifficulty.HARD,
                OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT,
                false, exclusion, "추천에서 뺐다.", "Excluded from recommendations.", null, null);
    }

    private List<Long> walk(OssRepo repo, String size, String difficulty) throws Exception {
        List<Long> walked = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder request = get(URL, repo.getId()).param("size", size);
            if (difficulty != null) {
                request.param("difficulty", difficulty);
            }
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            Map<String, Object> page = body(request, status().isOk());
            walked.addAll(idsOf(page));
            cursor = (String) page.get("nextCursor");
            pages++;
        } while (cursor != null && pages < MAX_PAGES);
        assertThat(cursor).isNull();
        return walked;
    }

    private List<Long> listedIds(MockHttpServletRequestBuilder request) throws Exception {
        return idsOf(body(request, status().isOk()));
    }

    private static List<Long> idsOf(Map<String, Object> page) {
        return items(page).stream()
                .map(item -> ((Number) item.get("id")).longValue())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("items");
    }

    private static List<Object> summaries(Map<String, Object> page) {
        return items(page).stream().map(item -> item.get("summary")).toList();
    }

    private static List<Map<String, Object>> withoutLanguage(Map<String, Object> page) {
        return items(page).stream()
                .map(item -> {
                    Map<String, Object> copy = new LinkedHashMap<>(item);
                    copy.remove("summary");
                    return copy;
                })
                .toList();
    }

    private OssRepo saveRepo(String fullName) {
        return ossRepoRepository.save(OssRepo.create(nextGithubId++, fullName, null, "Go", 12_000));
    }

    private OssIssue saveIssue(OssRepo repo, int number, LocalDateTime openedAt) {
        return ossIssueRepository.save(OssIssue.create(repo, 1_000L + nextGithubId++, number,
                "Stored title " + number, BODY, openedAt));
    }

    private void changeStoredBody(OssIssue issue, String body) {
        jdbcTemplate.update("UPDATE oss_issue SET body_hash = ? WHERE id = ?", OssIssueBodyHash.of(body),
                issue.getId());
    }

    private void suspend(OssRepo repo) {
        jdbcTemplate.update("UPDATE oss_repo SET status = 'SUSPENDED' WHERE id = ?", repo.getId());
    }

    private String latestGradedAt(OssIssue issue) {
        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM oss_issue_grade WHERE issue_id = ? ORDER BY id DESC LIMIT 1",
                LocalDateTime.class, issue.getId());
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(createdAt);
    }

    private Map<String, Object> notFoundBody(Long repoId) throws Exception {
        Map<String, Object> body = body(get(URL, repoId), status().isNotFound());
        body.remove("path");
        body.remove("timestamp");
        return body;
    }

    private Map<String, Object> body(MockHttpServletRequestBuilder request, ResultMatcher expectedStatus)
            throws Exception {
        String content = mockMvc.perform(request)
                .andExpect(expectedStatus)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readerForMapOf(Object.class).readValue(content);
    }
}
