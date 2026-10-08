package uhsuhjupjup.backend.oss.github.infra;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentials;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentialsMissingException;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueDetail;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueLookup;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static uhsuhjupjup.backend.oss.github.infra.MockGitHubServer.json;
import static uhsuhjupjup.backend.oss.github.infra.MockGitHubServer.redirect;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RestGitHubClientIssueTest {

    private static final String TOKEN = "ghp_issueToken";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(10);
    private static final long REPOSITORY_ID = 1_296_269L;
    private static final int NUMBER = 1347;
    private static final String ISSUE_PATH = "/repositories/1296269/issues/1347";
    private static final int RATE_LIMIT = 5000;
    private static final long RATE_LIMIT_RESET = Instant.parse("2026-10-07T18:00:00Z").getEpochSecond();
    private static final String RATE_LIMIT_RESET_KST = "2026-10-08T03:00:00+09:00";
    private static final String TITLE = "Found a bug";
    private static final String BODY = "I'm having a problem with this.";
    private static final String FOUND_A_BUG = """
            {
              "id": 5612345678,
              "node_id": "I_kwDOABCD5M6ABCDE",
              "url": "https://api.github.com/repos/octocat/Hello-World/issues/1347",
              "repository_url": "https://api.github.com/repos/octocat/Hello-World",
              "html_url": "https://github.com/octocat/Hello-World/issues/1347",
              "number": 1347,
              "state": "open",
              "state_reason": null,
              "title": "Found a bug",
              "body": "I'm having a problem with this.",
              "user": {"login": "octocat", "id": 1, "type": "User", "site_admin": false},
              "labels": [
                {"id": 208045946, "name": "bug", "default": true},
                {"id": 208045947, "name": "good first issue", "default": false}
              ],
              "assignee": null,
              "assignees": [],
              "milestone": null,
              "locked": false,
              "comments": 3,
              "closed_at": null,
              "created_at": "2026-09-28T08:11:25Z",
              "updated_at": "2026-09-28T15:30:00Z",
              "author_association": "OWNER"
            }
            """;

    @Mock
    private GitHubCredentials credentials;

    private MockGitHubServer github;
    private RestGitHubClient client;

    @BeforeEach
    void setUp() throws IOException {
        github = new MockGitHubServer("127.0.0.1");
        client = new RestGitHubClient(RestClient.builder(), credentials, github.url(""), CONNECT_TIMEOUT, READ_TIMEOUT,
                MAX_ATTEMPTS, RETRY_BACKOFF);
    }

    @AfterEach
    void tearDown() {
        github.stop();
    }

    @Test
    void findIssue_ok_asksByRepositoryIdAndMapsFieldsWithGitHubHeaders() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, FOUND_A_BUG));

        assertThat(client.findIssue(REPOSITORY_ID, NUMBER)).isEqualTo(GitHubIssueLookup.found(new GitHubIssueDetail(
                5612345678L, TITLE, BODY, List.of("bug", "good first issue"), true, false, 0, "octocat", "User")));

        assertThat(github.requests()).singleElement().satisfies(request -> {
            assertThat(request.rawPath()).isEqualTo(ISSUE_PATH);
            assertThat(request.rawQuery()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer " + TOKEN);
            assertThat(request.header("Accept")).isEqualTo("application/vnd.github+json");
            assertThat(request.header("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
            assertThat(request.header("User-Agent")).isEqualTo("UhsuhJupJup-OSS/1.0 (+https://www.uhsuh.com)");
        });
    }

    @Test
    void findIssue_withoutNumberOrTimes_stillReadsTheIssue() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, """
                {"id": 5612345678, "title": "Found a bug", "state": "open", "body": "text"}
                """));

        assertThat(client.findIssue(REPOSITORY_ID, NUMBER).issue()).isEqualTo(new GitHubIssueDetail(
                5612345678L, TITLE, "text", List.of(), true, false, 0, null, null));
    }

    @ParameterizedTest
    @CsvSource({"closed, false", "CLOSED, false", "open, true", "OPEN, true"})
    void findIssue_state_isOpenOnlyForOpen(String state, boolean open) {
        givenToken();
        github.respond(exchange -> json(exchange, 200, FOUND_A_BUG.replace("\"state\": \"open\"",
                "\"state\": \"" + state + "\"")));

        assertThat(client.findIssue(REPOSITORY_ID, NUMBER).issue().open()).isEqualTo(open);
    }

    @Test
    void findIssue_pullRequestKey_marksOnlyPresentAndNonNullValueAsPullRequest() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, switch (exchange.getRequestURI().getPath()) {
            case "/repositories/1296269/issues/31" ->
                    issue(31, "\"pull_request\": {\"url\": \"x\", \"merged_at\": null}");
            case "/repositories/1296269/issues/32" -> issue(32, "\"pull_request\": null");
            default -> issue(33, "\"locked\": false");
        }));

        assertThat(client.findIssue(REPOSITORY_ID, 31).issue().pullRequest()).isTrue();
        assertThat(client.findIssue(REPOSITORY_ID, 32).issue().pullRequest()).isFalse();
        assertThat(client.findIssue(REPOSITORY_ID, 33).issue().pullRequest()).isFalse();
    }

    @Test
    void findIssue_labels_keepNamesOfLabelObjectsAndPlainStringsSkippingBlankOrMissingNames() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, issue(41, """
                "labels": [{"name": "bug"}, "needs triage", {"name": ""}, {"name": "  "}, {"id": 7}, null,
                           {"name": 3}, {"name": "good first issue"}]""")));

        assertThat(client.findIssue(REPOSITORY_ID, 41).issue().labels())
                .isEqualTo(List.of("bug", "needs triage", "good first issue"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"labels\": []", "\"labels\": null", "\"labels\": \"bug\"", "\"locked\": false"})
    void findIssue_noLabelArray_givesNoLabels(String labels) {
        givenToken();
        github.respond(exchange -> json(exchange, 200, issue(42, labels)));

        assertThat(client.findIssue(REPOSITORY_ID, 42).issue().labels()).isEqualTo(List.of());
    }

    @Test
    void findIssue_assigneesAuthorAndBody_keepGitHubValuesAndDefaultsWhenAbsent() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, switch (exchange.getRequestURI().getPath()) {
            case "/repositories/1296269/issues/51" -> issue(51, """
                    "assignees": [{"login": "hubot"}, {"login": "octocat"}],
                    "user": {"login": "dependabot[bot]", "type": "Bot"}, "body": \"\"""");
            default -> issue(52, "\"assignees\": null, \"user\": null, \"body\": null");
        }));

        assertThat(client.findIssue(REPOSITORY_ID, 51).issue()).satisfies(issue -> {
            assertThat(issue.assigneeCount()).isEqualTo(2);
            assertThat(issue.authorLogin()).isEqualTo("dependabot[bot]");
            assertThat(issue.authorType()).isEqualTo("Bot");
            assertThat(issue.body()).isEmpty();
        });
        assertThat(client.findIssue(REPOSITORY_ID, 52).issue()).satisfies(issue -> {
            assertThat(issue.assigneeCount()).isZero();
            assertThat(issue.authorLogin()).isNull();
            assertThat(issue.authorType()).isNull();
            assertThat(issue.body()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void findIssue_notFoundOrGone_isGoneWithoutRetry(int status) {
        givenToken();
        github.respond(exchange -> json(exchange, status, "{\"message\":\"Not Found\"}"));

        assertThat(client.findIssue(REPOSITORY_ID, NUMBER)).isEqualTo(GitHubIssueLookup.gone());
        assertThat(github.requests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void findIssue_redirect_isMovedWithoutFollowingIt(int status, CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, 4990);
            redirect(exchange, status, github.url("/repositories/4242/issues/7"));
        });

        assertThat(client.findIssue(REPOSITORY_ID, NUMBER)).isEqualTo(GitHubIssueLookup.moved());
        assertThat(github.requests()).extracting(MockGitHubServer.ReceivedRequest::rawPath).containsExactly(ISSUE_PATH);
        assertThat(rateLimitLines(output)).singleElement().asString().contains(ISSUE_PATH + " 상태 " + status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/repositories/1296269/issues/1347?moved=true", "/repos/octocat/Hello-World/issues/1347",
            "https://elsewhere.example.com/issues/7", ""})
    void findIssue_redirectAnywhere_isMovedWithoutReadingItsLocation(String location) {
        givenToken();
        github.respond(exchange -> redirect(exchange, 301, location.startsWith("/") ? github.url(location) : location));

        assertThat(client.findIssue(REPOSITORY_ID, NUMBER)).isEqualTo(GitHubIssueLookup.moved());
        assertThat(github.requests()).hasSize(1);
    }

    @Test
    void findIssue_redirectToOtherHost_sendsNothingThere() throws IOException {
        givenToken();
        MockGitHubServer otherHost = new MockGitHubServer("localhost");
        try {
            otherHost.respond(exchange -> json(exchange, 200, FOUND_A_BUG));
            github.respond(exchange -> redirect(exchange, 301, otherHost.url("/repositories/4242/issues/7")));

            assertThat(client.findIssue(REPOSITORY_ID, NUMBER)).isEqualTo(GitHubIssueLookup.moved());
            assertThat(otherHost.requests()).isEmpty();
        } finally {
            otherHost.stop();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "401, UNAUTHORIZED",
            "403, REJECTED",
            "429, RATE_LIMITED",
            "400, REJECTED",
            "422, REJECTED",
            "451, REJECTED"
    })
    void findIssue_clientError_failsWithReasonAndStatusWithoutRetryAfterLoggingRateLimit(int status, Reason reason,
                                                                                         CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, 4321);
            json(exchange, status, "{\"message\":\"client error\"}");
        });

        assertThatThrownBy(() -> client.findIssue(REPOSITORY_ID, NUMBER))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(reason);
                    assertThat(e.getStatusCode()).hasValue(status);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(1);
        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains("4321/5000").contains(ISSUE_PATH + " 상태 " + status);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,    NONE",
            "4000, 60"
    }, nullValues = "NONE")
    void findIssue_forbiddenWithExhaustedLimitOrRetryAfter_failsAsRateLimited(String remaining, String retryAfter) {
        givenToken();
        github.respond(exchange -> {
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", remaining);
            if (retryAfter != null) {
                exchange.getResponseHeaders().set("Retry-After", retryAfter);
            }
            json(exchange, 403, "{\"message\":\"API rate limit exceeded\"}");
        });

        assertThatThrownBy(() -> client.findIssue(REPOSITORY_ID, NUMBER))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.RATE_LIMITED);
                    assertThat(e.getStatusCode()).hasValue(403);
                });
        assertThat(github.requests()).hasSize(1);
    }

    @Test
    void findIssue_serverErrorEveryTime_triesMaxAttemptsThenReportsUnavailable(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> json(exchange, 502, "{\"message\":\"Server Error\"}"));

        assertThatThrownBy(() -> client.findIssue(REPOSITORY_ID, NUMBER))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(MAX_ATTEMPTS);
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("Bearer");
    }

    @Test
    void findIssue_serverErrorThenOk_returnsIssue() {
        givenToken();
        AtomicInteger calls = new AtomicInteger();
        github.respond(exchange -> {
            if (calls.incrementAndGet() == 1) {
                json(exchange, 503, "{\"message\":\"Service Unavailable\"}");
                return;
            }
            json(exchange, 200, FOUND_A_BUG);
        });

        assertThat(client.findIssue(REPOSITORY_ID, NUMBER).status()).isEqualTo(GitHubIssueLookup.Status.FOUND);
        assertThat(github.requests()).hasSize(2);
    }

    static Stream<String> unreadableIssues() {
        return Stream.of(
                "not a json",
                "{}",
                "null",
                "[]",
                FOUND_A_BUG.replace("\"id\": 5612345678,", ""),
                FOUND_A_BUG.replace("\"title\": \"Found a bug\",", ""),
                FOUND_A_BUG.replace("\"state\": \"open\",", ""));
    }

    @ParameterizedTest
    @MethodSource("unreadableIssues")
    void findIssue_unreadableIssue_failsAsInvalidResponseWithoutRetryOrLeakingBody(String body) {
        givenToken();
        github.respond(exchange -> json(exchange, 200, body));

        assertThatThrownBy(() -> client.findIssue(REPOSITORY_ID, NUMBER))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.INVALID_RESPONSE);
                    assertThat(e.getMessage()).doesNotContain(BODY);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(1);
    }

    @Test
    void findIssue_rateLimit_isLoggedOnceWithPathAndStatusWithoutTokenOrBody(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, 4990);
            json(exchange, 200, FOUND_A_BUG);
        });

        client.findIssue(REPOSITORY_ID, NUMBER);

        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains("INFO")
                .contains("4990/5000")
                .contains(RATE_LIMIT_RESET_KST)
                .contains(ISSUE_PATH + " 상태 200");
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("Bearer").doesNotContain(BODY);
    }

    @ParameterizedTest
    @CsvSource({"501, INFO", "500, WARN"})
    void findIssue_rateLimitAtOrBelowTenPercent_isLoggedAsWarning(int remaining, String level,
                                                                  CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, remaining);
            json(exchange, 200, FOUND_A_BUG);
        });

        client.findIssue(REPOSITORY_ID, NUMBER);

        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains(level)
                .contains(remaining + "/" + RATE_LIMIT);
    }

    @Test
    void findIssue_withoutToken_sendsNoRequest() {
        given(credentials.isConfigured()).willReturn(false);

        assertThatThrownBy(() -> client.findIssue(REPOSITORY_ID, NUMBER))
                .isInstanceOfSatisfying(GitHubCredentialsMissingException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.NOT_CONFIGURED));
        assertThat(github.requests()).isEmpty();
        then(credentials).should(never()).authorizationHeader();
    }

    private void givenToken() {
        given(credentials.isConfigured()).willReturn(true);
        given(credentials.authorizationHeader()).willReturn("Bearer " + TOKEN);
    }

    private static String issue(long number, String extraFields) {
        return """
                {"id": %d, "number": %d, "title": "Issue %d", "state": "open",
                 "created_at": "2026-09-28T00:00:00Z", %s}
                """.formatted(number, number, number, extraFields);
    }

    private static void rateLimit(HttpExchange exchange, long remaining) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("X-RateLimit-Limit", String.valueOf(RATE_LIMIT));
        headers.set("X-RateLimit-Remaining", String.valueOf(remaining));
        headers.set("X-RateLimit-Reset", String.valueOf(RATE_LIMIT_RESET));
    }

    private static List<String> rateLimitLines(CapturedOutput output) {
        return output.getAll().lines()
                .filter(line -> line.contains("GitHub 한도"))
                .toList();
    }

    private static void assertNoTokenIn(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            assertThat(cause.toString()).doesNotContain(TOKEN).doesNotContain("Bearer");
        }
    }
}
