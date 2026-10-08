package uhsuhjupjup.backend.oss.issue;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class OssIssueDetailIntegrationTest {

    private static final String URL = "/api/oss/issues/{issueId}";
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 7, 12, 0, 0);
    private static final String BODY = "Steps to reproduce\n1. run it";
    private static final String EDITED_BODY = "Steps to reproduce\n1. run it twice";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final OssIssueVerdict VERDICT = new OssIssueVerdict(OssIssueDifficulty.MEDIUM,
            OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.ABSENT, OssIssueEvidence.PARTIAL,
            false, null, "재현 절차는 있지만 원인이 없다.", "Steps are given but the cause is missing.",
            "종료 뒤 워커가 남는다.", "Workers linger after shutdown.");
    private static final OssIssueVerdict REGRADED = new OssIssueVerdict(OssIssueDifficulty.EASY,
            OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT,
            true, null, "원인과 수정 방향까지 있다.", "The cause and the fix are given too.",
            "종료 훅 순서 때문에 워커가 남는다.", "Workers linger because of the shutdown hook order.");
    private static final OssIssueVerdict SPAM = new OssIssueVerdict(OssIssueDifficulty.HARD,
            OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT,
            false, OssIssueGradeExclusion.SPAM, "광고 링크만 있다.", "It only contains promotional links.",
            null, null);

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
    void detail_ofIssueGradedByTheGradingRun_returnsItWithoutLoginInKoreanByDefault() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1284);
        gradeByRun(repo, issue, BODY, VERDICT);

        mockMvc.perform(get(URL, issue.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(issue.getId()))
                .andExpect(jsonPath("$.number").value(1284))
                .andExpect(jsonPath("$.title").value("Stored title 1284"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/acme/fastqueue/issues/1284"))
                .andExpect(jsonPath("$.githubCreatedAt").value("2026-10-07T12:00:00"))
                .andExpect(jsonPath("$.repo.id").value(repo.getId()))
                .andExpect(jsonPath("$.repo.fullName").value("acme/fastqueue"))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.evidence.problem").value("PRESENT"))
                .andExpect(jsonPath("$.evidence.reproduction").value("PRESENT"))
                .andExpect(jsonPath("$.evidence.cause").value("ABSENT"))
                .andExpect(jsonPath("$.evidence.fixDirection").value("PARTIAL"))
                .andExpect(jsonPath("$.evidence.relatedPr").value(false))
                .andExpect(jsonPath("$.lang").value("ko"))
                .andExpect(jsonPath("$.reason").value("재현 절차는 있지만 원인이 없다."))
                .andExpect(jsonPath("$.summary").value("종료 뒤 워커가 남는다."))
                .andExpect(jsonPath("$.gradedAt").value(latestGradedAt(issue)));
    }

    @Test
    void detail_inEnglish_changesOnlyLangReasonAndSummary() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1284);
        gradeByRun(repo, issue, BODY, VERDICT);

        Map<String, Object> korean = body(get(URL, issue.getId()).param("lang", "ko"), status().isOk());
        Map<String, Object> english = body(get(URL, issue.getId()).param("lang", "en"), status().isOk());

        assertThat(english)
                .containsEntry("lang", "en")
                .containsEntry("reason", "Steps are given but the cause is missing.")
                .containsEntry("summary", "Workers linger after shutdown.");
        assertThat(korean)
                .containsEntry("lang", "ko")
                .containsEntry("reason", "재현 절차는 있지만 원인이 없다.")
                .containsEntry("summary", "종료 뒤 워커가 남는다.");
        assertThat(withoutLanguageFields(english)).isEqualTo(withoutLanguageFields(korean));
    }

    @Test
    void detail_emptyLang_answersInKorean() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1);
        gradeByRun(repo, issue, BODY, VERDICT);

        mockMvc.perform(get(URL, issue.getId()).param("lang", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("ko"))
                .andExpect(jsonPath("$.reason").value("재현 절차는 있지만 원인이 없다."));
    }

    @Test
    void detail_returnsOnlyPublicFields() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1);
        gradeByRun(repo, issue, BODY, VERDICT);

        Map<String, Object> body = body(get(URL, issue.getId()), status().isOk());

        assertThat(body).containsOnlyKeys("id", "number", "title", "githubUrl", "githubCreatedAt", "repo",
                "difficulty", "evidence", "lang", "reason", "summary", "gradedAt");
        assertThat(body.get("repo")).asInstanceOf(MAP).containsOnlyKeys("id", "fullName");
        assertThat(body.get("evidence")).asInstanceOf(MAP)
                .containsOnlyKeys("problem", "reproduction", "cause", "fixDirection", "relatedPr");
    }

    @Test
    void detail_afterTheBodyChanges_returns404UntilTheNewBodyIsGraded() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1);
        gradeByRun(repo, issue, BODY, VERDICT);
        mockMvc.perform(get(URL, issue.getId())).andExpect(status().isOk());

        changeStoredBody(issue, EDITED_BODY);

        mockMvc.perform(get(URL, issue.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_ISSUE_NOT_FOUND"));

        gradeByRun(repo, issue, EDITED_BODY, REGRADED);

        mockMvc.perform(get(URL, issue.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andExpect(jsonPath("$.reason").value("원인과 수정 방향까지 있다."));
    }

    @Test
    void detail_regradedWithTheSameBody_showsTheLatestGrade() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1);
        gradeByRun(repo, issue, BODY, VERDICT);

        ossIssueGradeSaver.save(issue, REGRADED, MODEL, issue.getBodyHash());

        mockMvc.perform(get(URL, issue.getId()).param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andExpect(jsonPath("$.evidence.relatedPr").value(true))
                .andExpect(jsonPath("$.reason").value("The cause and the fix are given too."))
                .andExpect(jsonPath("$.summary").value("Workers linger because of the shutdown hook order."))
                .andExpect(jsonPath("$.gradedAt").value(latestGradedAt(issue)));
    }

    @Test
    void detail_currentGradeExcluded_returns404() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1);
        gradeByRun(repo, issue, BODY, SPAM);

        mockMvc.perform(get(URL, issue.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_ISSUE_NOT_FOUND"))
                .andExpect(jsonPath("$", not(hasKey("title"))));
    }

    @Test
    void detail_olderVisibleGradeOfTheSameBody_doesNotShowThroughANewerExcludedGrade() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1);
        gradeByRun(repo, issue, BODY, VERDICT);

        ossIssueGradeSaver.save(issue, SPAM, MODEL, issue.getBodyHash());

        mockMvc.perform(get(URL, issue.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_ISSUE_NOT_FOUND"));
    }

    @Test
    void detail_newerVisibleGradeOfTheSameBody_isShownAfterAnExcludedGrade() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue issue = saveIssue(repo, 1);
        gradeByRun(repo, issue, BODY, SPAM);

        ossIssueGradeSaver.save(issue, VERDICT, MODEL, issue.getBodyHash());

        mockMvc.perform(get(URL, issue.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"));
    }

    @Test
    void detail_unknownUngradedExcludedAndSuspendedRepoIssues_returnTheSame404() throws Exception {
        OssRepo repo = saveRepo("acme/fastqueue");
        OssIssue excluded = saveIssue(repo, 2);
        gradeByRun(repo, excluded, BODY, SPAM);
        OssIssue ungraded = saveIssue(repo, 1);
        OssRepo suspendedRepo = saveRepo("acme/suspended");
        OssIssue ofSuspendedRepo = saveIssue(suspendedRepo, 3);
        gradeByRun(suspendedRepo, ofSuspendedRepo, BODY, VERDICT);
        mockMvc.perform(get(URL, ofSuspendedRepo.getId())).andExpect(status().isOk());
        suspend(suspendedRepo);

        Map<String, Object> unknown = notFoundBody(ofSuspendedRepo.getId() + 1);

        assertThat(unknown)
                .containsEntry("code", "OSS_ISSUE_NOT_FOUND")
                .containsEntry("message", "이슈를 찾을 수 없습니다.")
                .containsEntry("status", 404)
                .doesNotContainKey("fieldErrors");
        assertThat(notFoundBody(ungraded.getId())).isEqualTo(unknown);
        assertThat(notFoundBody(excluded.getId())).isEqualTo(unknown);
        assertThat(notFoundBody(ofSuspendedRepo.getId())).isEqualTo(unknown);
    }

    @Test
    void detail_issueIdNotANumber_returns400WithIssueIdFieldError() throws Exception {
        mockMvc.perform(get(URL, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/oss/issues/abc"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("issueId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));
    }

    @Test
    void detail_unsupportedLang_returns400WithLangFieldErrorEvenForAnUnknownIssue() throws Exception {
        mockMvc.perform(get(URL, 1).param("lang", "KO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lang"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("형식이 올바르지 않습니다."));
    }

    private void gradeByRun(OssRepo repo, OssIssue issue, String fetchedBody, OssIssueVerdict verdict) {
        given(gitHubClient.findIssue(repo.getGithubId(), issue.getNumber()))
                .willReturn(GitHubIssueLookup.found(new GitHubIssueDetail(issue.getGithubIssueId(),
                        "Fetched title " + issue.getNumber(), fetchedBody, List.of("bug"), true, false, 0,
                        "octocat", "User")));
        given(issueGrader.grade(any(), any(), any())).willReturn(new IssueGradingResult(verdict, MODEL));

        assertThat(ossIssueGradingService.gradeRepo(repo.getId()).graded()).isEqualTo(1);
    }

    private OssRepo saveRepo(String fullName) {
        return ossRepoRepository.save(OssRepo.create(nextGithubId++, fullName, null, "Go", 12_000));
    }

    private OssIssue saveIssue(OssRepo repo, int number) {
        return ossIssueRepository.save(OssIssue.create(repo, 1_000L + nextGithubId++, number,
                "Stored title " + number, BODY, OPENED_AT));
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

    private Map<String, Object> notFoundBody(Long issueId) throws Exception {
        Map<String, Object> body = body(get(URL, issueId), status().isNotFound());
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

    private static Map<String, Object> withoutLanguageFields(Map<String, Object> body) {
        body.remove("lang");
        body.remove("reason");
        body.remove("summary");
        return body;
    }
}
