package uhsuhjupjup.backend.oss.pipeline.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.domain.Role;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssue;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
@EnabledIf("liveTestEnabled")
class OssIssueSyncLiveTest {

    private static final String OWNER = "spring-projects";
    private static final String NAME = "spring-boot";
    private static final String FULL_NAME = OWNER + "/" + NAME;
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String GITHUB_API = "https://api.github.com";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final HttpClient GITHUB = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private GitHubClient gitHubClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    static boolean liveTestEnabled() {
        String token = System.getenv("OSS_GITHUB_TOKEN");
        return "true".equals(System.getenv("OSS_GITHUB_LIVE_TEST")) && token != null && !token.isBlank();
    }

    @BeforeEach
    void signUpAdmin() {
        memberRepository.deleteAllInBatch();
        Member admin = Member.create("google", "admin-uid", "admin@example.com");
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        memberRepository.save(admin);
        given(firebaseTokenVerifier.verify(ADMIN_TOKEN))
                .willReturn(new AuthUser("google", "admin-uid", "admin@example.com", Instant.now()));
    }

    @Test
    void syncRealRepoTwice_storesIssuesAndAddsNoDuplicates() throws Exception {
        long remainingBefore = remainingCoreRequests();
        Long repoId = register();

        JsonNode first = syncIssues(repoId);
        Map<Long, Long> rowsAfterFirst = storedRowIdsByGithubIssueId(repoId);
        JsonNode second = syncIssues(repoId);
        Map<Long, Long> rowsAfterSecond = storedRowIdsByGithubIssueId(repoId);
        long remainingAfter = remainingCoreRequests();

        System.out.println("[LIVE] " + FULL_NAME + " 1차 수집 = " + first);
        System.out.println("[LIVE] " + FULL_NAME + " 2차 수집 = " + second);
        System.out.println("[LIVE] 저장된 이슈: 1차 뒤 " + rowsAfterFirst.size() + "건, 2차 뒤 " + rowsAfterSecond.size() + "건");
        System.out.println("[LIVE] GitHub 한도: 등록과 수집 두 번 전 " + remainingBefore + "회, 뒤 " + remainingAfter
                + "회 남음(" + (remainingBefore - remainingAfter) + "회 차감)");

        assertThat(first.path("created").asInt())
                .as("최근 7일 안에 바뀐 열린 이슈 중 저장할 이슈가 있어야 한다")
                .isPositive();
        assertThat(rowsAfterFirst).hasSize(first.path("created").asInt());
        assertThat(rowsAfterSecond).containsAllEntriesOf(rowsAfterFirst);
        assertThat(rowsAfterSecond).hasSize(rowsAfterFirst.size() + second.path("created").asInt());
    }

    @Test
    void reportWhetherSinceIncludesIssueUpdatedAtThatTime() {
        List<GitHubIssue> recent = gitHubClient
                .listOpenIssues(OWNER, NAME, null, LocalDateTime.now(KST).minusDays(7))
                .issues();
        assertThat(recent).as("최근 7일 안에 바뀐 열린 이슈가 있어야 since 경계를 잴 수 있다").isNotEmpty();
        GitHubIssue latest = recent.stream().max(Comparator.comparing(GitHubIssue::updatedAt)).orElseThrow();

        List<GitHubIssue> fromThatTime = gitHubClient.listOpenIssues(OWNER, NAME, null, latest.updatedAt()).issues();

        Optional<GitHubIssue> again = fromThatTime.stream()
                .filter(issue -> issue.githubId() == latest.githubId())
                .findFirst();
        System.out.println("[LIVE] since 경계: #" + latest.number() + "의 수정 시각 " + latest.updatedAt()
                + "(KST)을 since로 다시 읽어 " + fromThatTime.size() + "건, " + sinceVerdict(latest, again));
    }

    @Test
    void reportWhetherNotModifiedAnswerCarriesLink() throws Exception {
        String since = DateTimeFormatter.ISO_INSTANT.format(
                Instant.now().minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
        URI firstPage = URI.create(GITHUB_API + "/repos/" + FULL_NAME
                + "/issues?state=open&sort=updated&direction=asc&per_page=1&since=" + since);

        HttpResponse<Void> listed = GITHUB.send(request(firstPage, null), HttpResponse.BodyHandlers.discarding());
        Optional<String> etag = listed.headers().firstValue("ETag");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(etag).as("첫 응답의 ETag").isPresent();
        HttpResponse<Void> conditional = GITHUB.send(request(firstPage, etag.get()),
                HttpResponse.BodyHandlers.discarding());

        System.out.println("[LIVE] 조건부 요청 1차: 상태 " + listed.statusCode() + ", Link " + linkOf(listed)
                + ", 한도 " + remainingOf(listed) + "회 남음");
        System.out.println("[LIVE] 조건부 요청 2차(같은 ETag): 상태 " + conditional.statusCode() + ", Link "
                + linkOf(conditional) + ", 한도 " + remainingOf(conditional) + "회 남음");
    }

    private Long register() throws Exception {
        String body = mockMvc.perform(post("/api/admin/oss/repos")
                        .header(AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"" + FULL_NAME + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("id").asLong();
    }

    private JsonNode syncIssues(Long repoId) throws Exception {
        String body = mockMvc.perform(post("/api/admin/oss/repos/{repoId}/issues/sync", repoId)
                        .header(AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private Map<Long, Long> storedRowIdsByGithubIssueId(Long repoId) {
        Map<Long, Long> rowIds = new HashMap<>();
        jdbcTemplate.query("SELECT id, github_issue_id FROM oss_issue WHERE repo_id = ?",
                row -> {
                    rowIds.put(row.getLong("github_issue_id"), row.getLong("id"));
                }, repoId);
        return rowIds;
    }

    private long remainingCoreRequests() throws Exception {
        HttpResponse<String> response = GITHUB.send(request(URI.create(GITHUB_API + "/rate_limit"), null),
                HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body()).path("resources").path("core").path("remaining").asLong();
    }

    private static HttpRequest request(URI uri, String etag) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "UhsuhJupJup-OSS-LiveTest")
                .header(AUTHORIZATION, "Bearer " + System.getenv("OSS_GITHUB_TOKEN").strip())
                .GET();
        if (etag != null) {
            request.header("If-None-Match", etag);
        }
        return request.build();
    }

    private static String sinceVerdict(GitHubIssue latest, Optional<GitHubIssue> again) {
        if (again.isEmpty()) {
            return "그 이슈가 오지 않음(그 사이 닫히지 않았다면 since는 경계 시각을 빼고 그 뒤만 준다)";
        }
        if (again.get().updatedAt().equals(latest.updatedAt())) {
            return "그 이슈가 다시 옴(since는 경계 시각을 포함한다)";
        }
        return "그 사이 이슈가 다시 바뀌어 판단할 수 없음(다시 돌린다)";
    }

    private static String linkOf(HttpResponse<?> response) {
        return response.headers().firstValue("Link").map(link -> "있음 " + link).orElse("없음");
    }

    private static String remainingOf(HttpResponse<?> response) {
        return response.headers().firstValue("X-RateLimit-Remaining").orElse("모름");
    }
}
