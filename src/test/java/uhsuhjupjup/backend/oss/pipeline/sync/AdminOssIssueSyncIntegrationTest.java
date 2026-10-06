package uhsuhjupjup.backend.oss.pipeline.sync;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.domain.Role;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentialsMissingException;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssue;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueListResult;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
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
class AdminOssIssueSyncIntegrationTest {

    private static final String SYNC_URL = "/api/admin/oss/repos/{repoId}/issues/sync";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_TOKEN = "user-token";
    private static final String OWNER = "octocat";
    private static final String NAME = "Hello-World";
    private static final String ETAG = "W/\"d4e5f6\"";
    private static final String SYNC_LOCK_PREFIX = "ossIssueSync-";
    private static final int SYNC_LOCK_MINUTES = 20;
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 30, 10, 0, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 10, 6, 10, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockitoBean
    private GitHubClient gitHubClient;

    private long nextRepoGithubId = 1;

    @BeforeEach
    void setUp() {
        ossRepoRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        signUp(ADMIN_TOKEN, "admin-uid", "admin@example.com", Role.ADMIN);
        signUp(USER_TOKEN, "user-uid", "user@example.com", Role.USER);
    }

    @Test
    void syncIssues_asAdmin_storesIssuesPassingPrefilterAndReturnsSummary() throws Exception {
        Long repoId = saveRepo();
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(changed(
                issue(101, 1347, false, 0, "octocat", "User"),
                issue(102, 1348, true, 0, "octocat", "User"),
                issue(103, 1349, false, 0, "renovate[bot]", "Bot"),
                issue(104, 1350, false, 1, "octocat", "User")));

        mockMvc.perform(sync(ADMIN_TOKEN, repoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notModified").value(false))
                .andExpect(jsonPath("$.incompleteReason").value(nullValue()))
                .andExpect(jsonPath("$.received").value(4))
                .andExpect(jsonPath("$.excluded.PULL_REQUEST").value(1))
                .andExpect(jsonPath("$.excluded.BOT_AUTHOR").value(1))
                .andExpect(jsonPath("$.excluded.ASSIGNED").value(1))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.bodyChanged").value(0))
                .andExpect(jsonPath("$.bodyUnchanged").value(0));

        then(gitHubClient).should().listOpenIssues(any(), any(), any(), any());
        assertThat(storedGithubIssueIds()).containsExactly(101L);
    }

    @Test
    void syncIssues_sameIssuesAgain_storesNothingTwice() throws Exception {
        Long repoId = saveRepo();
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(changed(
                issue(201, 1, false, 0, "octocat", "User"),
                issue(202, 2, false, 0, "octocat", "User")));
        mockMvc.perform(sync(ADMIN_TOKEN, repoId)).andExpect(status().isOk());
        List<Long> rowIds = storedRowIds();

        mockMvc.perform(sync(ADMIN_TOKEN, repoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.bodyUnchanged").value(2));

        assertThat(storedGithubIssueIds()).containsExactly(201L, 202L);
        assertThat(storedRowIds()).hasSize(2).isEqualTo(rowIds);
    }

    @Test
    void syncIssues_asNonAdmin_returns403WithoutCallingGitHubOrTakingLock() throws Exception {
        Long repoId = saveRepo();

        mockMvc.perform(sync(USER_TOKEN, repoId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        then(gitHubClient).shouldHaveNoInteractions();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oss_repo_sync_state", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = ?", Long.class, SYNC_LOCK_PREFIX + repoId)).isZero();
    }

    @Test
    void syncIssues_asNonAdminWithNonNumericRepoId_returns403BeforeValidation() throws Exception {
        mockMvc.perform(post(SYNC_URL, "abc").header(AUTHORIZATION, "Bearer " + USER_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void syncIssues_nonNumericRepoId_returns400() throws Exception {
        mockMvc.perform(post(SYNC_URL, "abc").header(AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("repoId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));

        then(gitHubClient).shouldHaveNoInteractions();
    }

    @Test
    void syncIssues_missingOrSuspendedRepo_returns404WithoutCallingGitHub() throws Exception {
        Long suspended = saveRepo();
        jdbcTemplate.update("UPDATE oss_repo SET status = 'SUSPENDED' WHERE id = ?", suspended);

        mockMvc.perform(sync(ADMIN_TOKEN, suspended))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_REPO_NOT_FOUND"));
        mockMvc.perform(sync(ADMIN_TOKEN, suspended + 1000))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_REPO_NOT_FOUND"));

        then(gitHubClient).shouldHaveNoInteractions();
    }

    @Test
    void syncIssues_sameRepoTwiceAtOnce_answers409ToOneAndStoresIssuesOnce() throws Exception {
        Long repoId = saveRepo();
        CountDownLatch refused = new CountDownLatch(1);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willAnswer(invocation -> {
            refused.await(10, TimeUnit.SECONDS);
            return changed(
                    issue(301, 1, false, 0, "octocat", "User"),
                    issue(302, 2, false, 0, "octocat", "User"));
        });

        List<MockHttpServletResponse> responses = sendTogether(2, () -> {
            MockHttpServletResponse response = mockMvc.perform(sync(ADMIN_TOKEN, repoId)).andReturn().getResponse();
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
                .isEqualTo("OSS_ISSUE_SYNC_IN_PROGRESS");
        assertThat(JsonPath.<String>read(rejected.getContentAsString(), "$.message"))
                .isEqualTo("이 레포의 이슈 수집이 이미 진행 중입니다. 잠시 뒤 다시 시도해 주세요.");
        then(gitHubClient).should(times(1)).listOpenIssues(any(), any(), any(), any());
        assertThat(storedGithubIssueIds()).containsExactly(301L, 302L);
    }

    @Test
    void syncIssues_lockLeftByCrashedInstanceTwentyOneMinutesAgo_takesItOverAndRuns() throws Exception {
        Long repoId = saveRepo();
        leaveSyncLock(repoId, "crashed-instance", SYNC_LOCK_MINUTES + 1);
        given(gitHubClient.listOpenIssues(any(), any(), any(), any()))
                .willReturn(changed(issue(601, 1, false, 0, "octocat", "User")));

        mockMvc.perform(sync(ADMIN_TOKEN, repoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        assertThat(syncLockHolder(repoId)).isNotEqualTo("crashed-instance");
        assertThat(syncLockHeld(repoId)).isFalse();
    }

    @Test
    void syncIssues_lockHeldByAnotherInstance_returns409WithoutCallingGitHub() throws Exception {
        Long repoId = saveRepo();
        leaveSyncLock(repoId, "other-instance", 5);

        mockMvc.perform(sync(ADMIN_TOKEN, repoId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OSS_ISSUE_SYNC_IN_PROGRESS"));

        then(gitHubClient).shouldHaveNoInteractions();
        assertThat(syncLockHolder(repoId)).isEqualTo("other-instance");
        assertThat(syncLockHeld(repoId)).isTrue();
    }

    static Stream<Arguments> gitHubFailures() {
        return Stream.of(
                Arguments.of(new GitHubCredentialsMissingException(), "GITHUB_TOKEN_REQUIRED"),
                Arguments.of(new GitHubClientException(Reason.RATE_LIMITED, 403, "GitHub 요청 실패(상태 403)", null),
                        "GITHUB_UNAVAILABLE"),
                Arguments.of(new GitHubClientException(Reason.UNAVAILABLE, "GitHub가 응답하지 않습니다(시도 3번)", null),
                        "GITHUB_UNAVAILABLE"),
                Arguments.of(new GitHubClientException(Reason.REJECTED, 404, "GitHub 요청 실패(상태 404)", null),
                        "GITHUB_UNAVAILABLE"));
    }

    @ParameterizedTest
    @MethodSource("gitHubFailures")
    void syncIssues_githubFails_returns503AndReleasesLockForNextTry(GitHubClientException failure, String code)
            throws Exception {
        Long repoId = saveRepo();
        given(gitHubClient.listOpenIssues(any(), any(), any(), any()))
                .willThrow(failure)
                .willReturn(changed(issue(401, 1, false, 0, "octocat", "User")));

        mockMvc.perform(sync(ADMIN_TOKEN, repoId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(code));

        mockMvc.perform(sync(ADMIN_TOKEN, repoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
    }

    @Test
    void syncIssues_middlePageUnavailable_returns200WithReasonAndCountsOneFailure() throws Exception {
        Long repoId = saveRepo();
        given(gitHubClient.listOpenIssues(any(), any(), any(), any())).willReturn(GitHubIssueListResult.partial(
                List.of(issue(501, 1, false, 0, "octocat", "User")),
                GitHubIssueListResult.IncompleteReason.PAGE_UNAVAILABLE));

        mockMvc.perform(sync(ADMIN_TOKEN, repoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incompleteReason").value("PAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.created").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT consecutive_failures FROM oss_repo_sync_state WHERE repo_id = ?", Integer.class, repoId))
                .isEqualTo(1);
    }

    private void signUp(String token, String uid, String email, Role role) {
        Member member = Member.create("google", uid, email);
        ReflectionTestUtils.setField(member, "role", role);
        memberRepository.save(member);
        given(firebaseTokenVerifier.verify(token)).willReturn(new AuthUser("google", uid, email, Instant.now()));
    }

    private Long saveRepo() {
        return ossRepoRepository.save(OssRepo.create(nextRepoGithubId++, OWNER + "/" + NAME, null, "Java", 1_000))
                .getId();
    }

    private void leaveSyncLock(Long repoId, String lockedBy, int lockedMinutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
                VALUES (?, TIMESTAMPADD(MINUTE, ?, UTC_TIMESTAMP(3)), TIMESTAMPADD(MINUTE, ?, UTC_TIMESTAMP(3)), ?)
                """, SYNC_LOCK_PREFIX + repoId, SYNC_LOCK_MINUTES - lockedMinutesAgo, -lockedMinutesAgo, lockedBy);
    }

    private String syncLockHolder(Long repoId) {
        return jdbcTemplate.queryForObject(
                "SELECT locked_by FROM shedlock WHERE name = ?", String.class, SYNC_LOCK_PREFIX + repoId);
    }

    private boolean syncLockHeld(Long repoId) {
        return jdbcTemplate.queryForObject(
                "SELECT lock_until > UTC_TIMESTAMP(3) FROM shedlock WHERE name = ?", Boolean.class,
                SYNC_LOCK_PREFIX + repoId);
    }

    private MockHttpServletRequestBuilder sync(String token, Long repoId) {
        return post(SYNC_URL, repoId).header(AUTHORIZATION, "Bearer " + token);
    }

    private List<Long> storedGithubIssueIds() {
        return jdbcTemplate.queryForList("SELECT github_issue_id FROM oss_issue ORDER BY github_issue_id", Long.class);
    }

    private List<Long> storedRowIds() {
        return jdbcTemplate.queryForList("SELECT id FROM oss_issue ORDER BY github_issue_id", Long.class);
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

    private static GitHubIssueListResult changed(GitHubIssue... issues) {
        return GitHubIssueListResult.changed(ETAG, List.of(issues));
    }

    private static GitHubIssue issue(long githubId, int number, boolean pullRequest, int assigneeCount,
                                     String authorLogin, String authorType) {
        return new GitHubIssue(githubId, number, "Issue " + number, "Steps to reproduce " + number, pullRequest,
                assigneeCount, authorLogin, authorType, OPENED_AT, UPDATED_AT);
    }
}
