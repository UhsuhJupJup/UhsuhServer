package uhsuhjupjup.backend.oss.pipeline.grading;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.domain.Role;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentialsMissingException;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueDetail;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueLookup;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGrader;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class AdminOssIssueGradingIntegrationTest {

    private static final String GRADE_URL = "/api/admin/oss/repos/{repoId}/issues/grade";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_TOKEN = "user-token";
    private static final String GRADING_LOCK_PREFIX = "ossIssueGrade-";
    private static final int GRADING_LOCK_MINUTES = 15;
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 7, 12, 0, 0);
    private static final String BODY = "Steps to reproduce\n1. run it";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final OssIssueVerdict VERDICT = new OssIssueVerdict(OssIssueDifficulty.MEDIUM,
            OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.ABSENT, OssIssueEvidence.PARTIAL,
            false, null, "재현 절차는 있지만 원인이 없다.", "Steps are given but the cause is missing.",
            "종료 뒤 워커가 남는다.", "Workers linger after shutdown.");
    private static final IssueGradingResult GRADED = new IssueGradingResult(VERDICT, MODEL);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private OssIssueRepository ossIssueRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockitoBean
    private GitHubClient gitHubClient;

    @MockitoBean
    private IssueGrader issueGrader;

    private long nextRepoGithubId = 1;

    @BeforeEach
    void setUp() {
        ossRepoRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        signUp(ADMIN_TOKEN, "admin-uid", "admin@example.com", Role.ADMIN);
        signUp(USER_TOKEN, "user-uid", "user@example.com", Role.USER);
    }

    @Test
    void gradeIssues_asAdminWithALimit_gradesTheNewestThatManyAndReturnsTheSummary() throws Exception {
        OssRepo repo = saveRepo();
        saveIssues(repo, 4);
        givenEveryIssueFoundAndGraded(repo);

        mockMvc.perform(grade(ADMIN_TOKEN, repo.getId()).param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopReason").value("ISSUE_LIMIT"))
                .andExpect(jsonPath("$.selected").value(3))
                .andExpect(jsonPath("$.graded").value(3))
                .andExpect(jsonPath("$.held").value(0))
                .andExpect(jsonPath("$.notStarted").value(0))
                .andExpect(jsonPath("$.sameBodySkipped").value(0))
                .andExpect(jsonPath("$.failureLimitSkipped").value(0))
                .andExpect(jsonPath("$.githubSkipped").value(0))
                .andExpect(jsonPath("$.ungradable.CLOSED").value(0))
                .andExpect(jsonPath("$.failed.INVALID_OUTPUT").value(0));

        assertThat(gradedNumbers(repo)).containsExactly(2, 3, 4);
        then(gitHubClient).should(times(3)).findIssue(eq(repo.getGithubId()), anyInt());
        assertThat(gradingLockHeld(repo.getId())).isFalse();
    }

    @Test
    void gradeIssues_withoutLimit_gradesUpToFive() throws Exception {
        OssRepo repo = saveRepo();
        saveIssues(repo, 6);
        givenEveryIssueFoundAndGraded(repo);

        mockMvc.perform(grade(ADMIN_TOKEN, repo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopReason").value("ISSUE_LIMIT"))
                .andExpect(jsonPath("$.selected").value(5))
                .andExpect(jsonPath("$.graded").value(5));

        assertThat(gradedNumbers(repo)).containsExactly(2, 3, 4, 5, 6);
    }

    @Test
    void gradeIssues_limitAboveWhatIsLeft_completesWithTheRest() throws Exception {
        OssRepo repo = saveRepo();
        saveIssues(repo, 2);
        givenEveryIssueFoundAndGraded(repo);

        mockMvc.perform(grade(ADMIN_TOKEN, repo.getId()).param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopReason").value("COMPLETED"))
                .andExpect(jsonPath("$.selected").value(2))
                .andExpect(jsonPath("$.graded").value(2));
    }

    @Test
    void gradeIssues_callsGitHubAndTheGraderOutsideAnyTransactionWithTheIssueIdInTheLogContext() throws Exception {
        OssRepo repo = saveRepo();
        OssIssue issue = saveIssue(repo, 1, OPENED_AT);
        List<String> calls = new CopyOnWriteArrayList<>();
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willAnswer(invocation -> {
            calls.add("fetch transaction=" + TransactionSynchronizationManager.isActualTransactionActive()
                    + " issueId=" + MDC.get(IssueGrader.ISSUE_ID_LOG_KEY));
            return found(1);
        });
        given(issueGrader.grade(any(), any(), any())).willAnswer(invocation -> {
            calls.add("grade transaction=" + TransactionSynchronizationManager.isActualTransactionActive()
                    + " issueId=" + MDC.get(IssueGrader.ISSUE_ID_LOG_KEY));
            return GRADED;
        });

        mockMvc.perform(grade(ADMIN_TOKEN, repo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graded").value(1));

        assertThat(calls).containsExactly(
                "fetch transaction=false issueId=" + issue.getId(),
                "grade transaction=false issueId=" + issue.getId());
    }

    @Test
    void gradeIssues_graderUnavailableMidway_returns200WithTheReasonAndKeepsWhatWasGradedBeforeIt() throws Exception {
        OssRepo repo = saveRepo();
        saveIssues(repo, 3);
        given(gitHubClient.findIssue(eq(repo.getGithubId()), anyInt()))
                .willAnswer(invocation -> found(invocation.getArgument(1)));
        given(issueGrader.grade(any(), any(), any()))
                .willReturn(GRADED)
                .willThrow(new IssueGradingException(Reason.UNAVAILABLE, "이슈를 판정하지 못했습니다: Claude, GPT 실패"));

        mockMvc.perform(grade(ADMIN_TOKEN, repo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopReason").value("GRADER_UNAVAILABLE"))
                .andExpect(jsonPath("$.selected").value(3))
                .andExpect(jsonPath("$.graded").value(1))
                .andExpect(jsonPath("$.held").value(1))
                .andExpect(jsonPath("$.notStarted").value(1));

        assertThat(gradedNumbers(repo)).containsExactly(3);
        assertThat(gradingLockHeld(repo.getId())).isFalse();
    }

    @Test
    void gradeIssues_withoutGitHubToken_returns200WithTheReasonInsteadOfAnError() throws Exception {
        OssRepo repo = saveRepo();
        saveIssues(repo, 2);
        given(gitHubClient.findIssue(anyLong(), anyInt())).willThrow(new GitHubCredentialsMissingException());

        mockMvc.perform(grade(ADMIN_TOKEN, repo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopReason").value("GITHUB_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.selected").value(2))
                .andExpect(jsonPath("$.held").value(1))
                .andExpect(jsonPath("$.notStarted").value(1));

        then(issueGrader).shouldHaveNoInteractions();
    }

    @Test
    void gradeIssues_lockHeldByAnotherInstance_returns409WithoutCallingGitHub() throws Exception {
        OssRepo repo = saveRepo();
        saveIssues(repo, 1);
        leaveGradingLock(repo.getId(), "other-instance", 5);

        mockMvc.perform(grade(ADMIN_TOKEN, repo.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OSS_ISSUE_GRADING_IN_PROGRESS"));

        then(gitHubClient).shouldHaveNoInteractions();
        assertThat(gradingLockHolder(repo.getId())).isEqualTo("other-instance");
        assertThat(gradingLockHeld(repo.getId())).isTrue();
    }

    @Test
    void gradeIssues_sameRepoTwiceAtOnce_answers409ToOneAndGradesOnce() throws Exception {
        OssRepo repo = saveRepo();
        saveIssues(repo, 1);
        CountDownLatch refused = new CountDownLatch(1);
        given(gitHubClient.findIssue(repo.getGithubId(), 1)).willReturn(found(1));
        given(issueGrader.grade(any(), any(), any())).willAnswer(invocation -> {
            refused.await(10, TimeUnit.SECONDS);
            return GRADED;
        });

        List<MockHttpServletResponse> responses = sendTogether(2, () -> {
            MockHttpServletResponse response =
                    mockMvc.perform(grade(ADMIN_TOKEN, repo.getId())).andReturn().getResponse();
            if (response.getStatus() == 409) {
                refused.countDown();
            }
            return response;
        });

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
        MockHttpServletResponse rejected = responses.stream()
                .filter(response -> response.getStatus() == 409)
                .findFirst()
                .orElseThrow();
        assertThat(JsonPath.<String>read(rejected.getContentAsString(), "$.code"))
                .isEqualTo("OSS_ISSUE_GRADING_IN_PROGRESS");
        assertThat(JsonPath.<String>read(rejected.getContentAsString(), "$.message"))
                .isEqualTo("이 레포의 이슈 판정이 이미 진행 중입니다. 잠시 뒤 다시 시도해 주세요.");
        then(issueGrader).should(times(1)).grade(any(), any(), any());
        assertThat(gradedNumbers(repo)).containsExactly(1);
    }

    @Test
    void gradeIssues_asNonAdmin_returns403WithoutTakingTheLockOrCallingGitHub() throws Exception {
        OssRepo repo = saveRepo();
        saveIssues(repo, 1);

        mockMvc.perform(grade(USER_TOKEN, repo.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        then(gitHubClient).shouldHaveNoInteractions();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shedlock WHERE name = ?", Long.class,
                GRADING_LOCK_PREFIX + repo.getId())).isZero();
    }

    @Test
    void gradeIssues_missingOrSuspendedRepo_returns404WithoutCallingGitHub() throws Exception {
        OssRepo suspended = saveRepo();
        saveIssues(suspended, 1);
        jdbcTemplate.update("UPDATE oss_repo SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());

        mockMvc.perform(grade(ADMIN_TOKEN, suspended.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_REPO_NOT_FOUND"));
        mockMvc.perform(grade(ADMIN_TOKEN, suspended.getId() + 1000))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_REPO_NOT_FOUND"));

        then(gitHubClient).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "21", "abc"})
    void gradeIssues_limitOutOfRangeOrNotANumber_returns400WithoutTakingTheLock(String limit) throws Exception {
        OssRepo repo = saveRepo();
        saveIssues(repo, 1);

        mockMvc.perform(grade(ADMIN_TOKEN, repo.getId()).param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"));

        then(gitHubClient).shouldHaveNoInteractions();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shedlock WHERE name = ?", Long.class,
                GRADING_LOCK_PREFIX + repo.getId())).isZero();
    }

    private void signUp(String token, String uid, String email, Role role) {
        Member member = Member.create("google", uid, email);
        ReflectionTestUtils.setField(member, "role", role);
        memberRepository.save(member);
        given(firebaseTokenVerifier.verify(token)).willReturn(new AuthUser("google", uid, email, Instant.now()));
    }

    private OssRepo saveRepo() {
        return ossRepoRepository.save(OssRepo.create(nextRepoGithubId++, "octocat/Hello-World", null, "Java", 1_000));
    }

    private void saveIssues(OssRepo repo, int count) {
        IntStream.rangeClosed(1, count).forEach(number -> saveIssue(repo, number, OPENED_AT.plusMinutes(number)));
    }

    private OssIssue saveIssue(OssRepo repo, int number, LocalDateTime openedAt) {
        return ossIssueRepository.save(OssIssue.create(repo, 1_000L + number, number, "Stored title " + number, BODY,
                openedAt));
    }

    private void givenEveryIssueFoundAndGraded(OssRepo repo) {
        given(gitHubClient.findIssue(eq(repo.getGithubId()), anyInt()))
                .willAnswer(invocation -> found(invocation.getArgument(1)));
        given(issueGrader.grade(any(), any(), any())).willReturn(GRADED);
    }

    private static GitHubIssueLookup found(int number) {
        return GitHubIssueLookup.found(new GitHubIssueDetail(1_000L + number, "Fetched title " + number, BODY,
                List.of("bug"), true, false, 0, "octocat", "User"));
    }

    private MockHttpServletRequestBuilder grade(String token, Long repoId) {
        return post(GRADE_URL, repoId).header(AUTHORIZATION, "Bearer " + token);
    }

    private List<Integer> gradedNumbers(OssRepo repo) {
        return jdbcTemplate.queryForList("""
                SELECT i.number FROM oss_issue i
                WHERE i.repo_id = ?
                AND EXISTS (SELECT 1 FROM oss_issue_grade g WHERE g.issue_id = i.id)
                ORDER BY i.number
                """, Integer.class, repo.getId());
    }

    private void leaveGradingLock(Long repoId, String lockedBy, int lockedMinutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
                VALUES (?, TIMESTAMPADD(MINUTE, ?, UTC_TIMESTAMP(3)), TIMESTAMPADD(MINUTE, ?, UTC_TIMESTAMP(3)), ?)
                """, GRADING_LOCK_PREFIX + repoId, GRADING_LOCK_MINUTES - lockedMinutesAgo, -lockedMinutesAgo,
                lockedBy);
    }

    private String gradingLockHolder(Long repoId) {
        return jdbcTemplate.queryForObject(
                "SELECT locked_by FROM shedlock WHERE name = ?", String.class, GRADING_LOCK_PREFIX + repoId);
    }

    private boolean gradingLockHeld(Long repoId) {
        return jdbcTemplate.queryForObject(
                "SELECT lock_until > UTC_TIMESTAMP(3) FROM shedlock WHERE name = ?", Boolean.class,
                GRADING_LOCK_PREFIX + repoId);
    }

    private List<MockHttpServletResponse> sendTogether(int count, Callable<MockHttpServletResponse> request)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<MockHttpServletResponse>> futures =
                    executor.invokeAll(Collections.nCopies(count, request), 30, TimeUnit.SECONDS);
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get());
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }
}
