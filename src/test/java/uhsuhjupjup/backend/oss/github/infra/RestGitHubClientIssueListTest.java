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
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssue;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueListResult;
import uhsuhjupjup.backend.oss.github.infra.MockGitHubServer.ReceivedRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static uhsuhjupjup.backend.oss.github.infra.MockGitHubServer.json;
import static uhsuhjupjup.backend.oss.github.infra.MockGitHubServer.redirect;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RestGitHubClientIssueListTest {

    private static final String TOKEN = "ghp_issueListToken";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SHORT_READ_TIMEOUT = Duration.ofMillis(300);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(10);
    private static final String OWNER = "octocat";
    private static final String NAME = "Hello-World";
    private static final String ISSUES_PATH = "/repos/octocat/Hello-World/issues";
    private static final String ISSUES_BY_REPO_ID_PATH = "/repositories/1296269/issues";
    private static final String ETAG = "W/\"a1b2c3\"";
    private static final String NEW_ETAG = "W/\"d4e5f6\"";
    private static final String OTHER_ETAG_ON_NOT_MODIFIED = "W/\"f7e8d9\"";
    private static final int RATE_LIMIT = 5000;
    private static final long RATE_LIMIT_RESET = Instant.parse("2026-09-29T18:00:00Z").getEpochSecond();
    private static final String RATE_LIMIT_RESET_KST = "2026-09-30T03:00:00+09:00";
    private static final Map<String, String> OPEN_ISSUES_BY_UPDATE = Map.of(
            "state", "open",
            "sort", "updated",
            "direction", "asc",
            "per_page", "100");
    private static final String FOUND_A_BUG = """
            [
              {
                "id": 5612345678,
                "node_id": "I_kwDOABCD5M6ABCDE",
                "url": "https://api.github.com/repos/octocat/Hello-World/issues/1347",
                "html_url": "https://github.com/octocat/Hello-World/issues/1347",
                "number": 1347,
                "state": "open",
                "title": "Found a bug",
                "body": "I'm having a problem with this.",
                "user": {"login": "octocat", "id": 1, "type": "User", "site_admin": false},
                "labels": [{"id": 208045946, "name": "bug", "default": true}],
                "assignee": {"login": "hubot", "id": 2, "type": "User"},
                "assignees": [
                  {"login": "hubot", "id": 2, "type": "User"},
                  {"login": "octocat", "id": 1, "type": "User"}
                ],
                "milestone": null,
                "locked": false,
                "comments": 3,
                "closed_at": null,
                "created_at": "2026-09-28T08:11:25Z",
                "updated_at": "2026-09-28T15:30:00Z",
                "author_association": "OWNER",
                "state_reason": null,
                "reactions": {"total_count": 0}
              }
            ]
            """;

    @Mock
    private GitHubCredentials credentials;

    private MockGitHubServer github;
    private RestGitHubClient client;

    @BeforeEach
    void setUp() throws IOException {
        github = new MockGitHubServer("127.0.0.1");
        client = clientWithReadTimeout(READ_TIMEOUT);
    }

    @AfterEach
    void tearDown() {
        github.stop();
    }

    @Test
    void listOpenIssues_firstTime_asksOpenIssuesInUpdateOrderAndMapsFields() {
        givenToken();
        github.respond(exchange -> {
            etag(exchange, NEW_ETAG);
            json(exchange, 200, FOUND_A_BUG);
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, null, null);

        assertThat(listed.notModified()).isFalse();
        assertThat(listed.complete()).isTrue();
        assertThat(listed.etag()).isEqualTo(NEW_ETAG);
        assertThat(listed.issues()).containsExactly(new GitHubIssue(
                5612345678L, 1347, "Found a bug", "I'm having a problem with this.", false, 2,
                "octocat", "User", LocalDateTime.of(2026, 9, 28, 17, 11, 25), LocalDateTime.of(2026, 9, 29, 0, 30)));
        assertThat(github.requests()).singleElement().satisfies(request -> {
            assertThat(request.rawPath()).isEqualTo(ISSUES_PATH);
            assertThat(request.queryParams()).isEqualTo(OPEN_ISSUES_BY_UPDATE);
            assertThat(request.header("If-None-Match")).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer " + TOKEN);
            assertThat(request.header("Accept")).isEqualTo("application/vnd.github+json");
            assertThat(request.header("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
        });
    }

    @Test
    void listOpenIssues_since_isSentInUtcAndGitHubTimesComeBackInKstWhateverTheSystemZone() {
        TimeZone systemZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        try {
            givenToken();
            github.respond(exchange -> json(exchange, 200, FOUND_A_BUG));

            GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, null,
                    LocalDateTime.of(2026, 9, 28, 17, 11, 25));

            assertThat(github.requests()).singleElement().satisfies(request ->
                    assertThat(request.queryParams()).containsEntry("since", "2026-09-28T08:11:25Z"));
            assertThat(listed.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 17, 11, 25));
                assertThat(issue.updatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 0, 30));
            });
        } finally {
            TimeZone.setDefault(systemZone);
        }
    }

    @Test
    void listOpenIssues_sinceWithFractionOfSecond_isSentInWholeSeconds() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, "[]"));

        client.listOpenIssues(OWNER, NAME, null, LocalDateTime.of(2026, 9, 21, 17, 11, 25, 987_654_321));

        assertThat(github.requests()).singleElement().satisfies(request ->
                assertThat(request.queryParams()).containsEntry("since", "2026-09-21T08:11:25Z"));
    }

    @Test
    void listOpenIssues_specialCharactersInOwnerOrName_areEncodedIntoPath() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, "[]"));

        client.listOpenIssues("octo/cat", "a b?c#d", null, null);

        assertThat(github.requests())
                .extracting(ReceivedRequest::rawPath)
                .containsExactly("/repos/octo%2Fcat/a%20b%3Fc%23d/issues");
    }

    @Test
    void listOpenIssues_noOpenIssues_returnsEmptyCompleteListWithNewEtag() {
        givenToken();
        github.respond(exchange -> {
            etag(exchange, NEW_ETAG);
            json(exchange, 200, "[]");
        });

        assertThat(client.listOpenIssues(OWNER, NAME, ETAG, null))
                .isEqualTo(GitHubIssueListResult.changed(NEW_ETAG, List.of()));
    }

    @Test
    void listOpenIssues_notModified_returnsNoIssuesKeepingSentEtagEvenIfResponseEtagDiffers(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, 4990);
            notModified(exchange, OTHER_ETAG_ON_NOT_MODIFIED);
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, ETAG, LocalDateTime.of(2026, 9, 28, 17, 11, 25));

        assertThat(listed).isEqualTo(GitHubIssueListResult.unchanged(ETAG));
        assertThat(listed.notModified()).isTrue();
        assertThat(listed.issues()).isEmpty();
        assertThat(listed.complete()).isTrue();
        assertThat(listed.etag()).isEqualTo(ETAG).isNotEqualTo(OTHER_ETAG_ON_NOT_MODIFIED);
        assertThat(github.requests()).singleElement().satisfies(request -> {
            assertThat(request.header("If-None-Match")).isEqualTo(ETAG);
            assertThat(request.queryParams()).containsEntry("since", "2026-09-28T08:11:25Z");
        });
        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains("INFO").contains("4990/5000").contains("상태 304");
    }

    @Test
    void listOpenIssues_notModifiedWithNextPage_rereadsFirstPageWithoutEtagToTheLastPage() {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            links(exchange, page, 2);
            if (exchange.getRequestHeaders().containsKey("If-None-Match")) {
                notModified(exchange, ETAG);
                return;
            }
            etag(exchange, page == 1 ? NEW_ETAG : "W/\"page-2\"");
            json(exchange, 200, issues(issue(page, "2026-09-28T0" + page + ":00:00Z")));
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, ETAG, LocalDateTime.of(2026, 9, 28, 9, 0));

        assertThat(listed.notModified()).isFalse();
        assertThat(listed.complete()).isTrue();
        assertThat(listed.etag()).isEqualTo(NEW_ETAG);
        assertThat(listed.issues()).extracting(GitHubIssue::githubId).containsExactly(1L, 2L);
        assertThat(github.requests())
                .extracting(ReceivedRequest::rawPath, request -> request.header("If-None-Match"))
                .containsExactly(
                        tuple(ISSUES_PATH, ETAG),
                        tuple(ISSUES_PATH, null),
                        tuple(ISSUES_BY_REPO_ID_PATH, null));
    }

    @Test
    void listOpenIssues_changedSinceEtag_returnsIssuesWithNewEtag() {
        givenToken();
        github.respond(exchange -> {
            etag(exchange, NEW_ETAG);
            json(exchange, 200, FOUND_A_BUG);
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, ETAG, null);

        assertThat(listed.notModified()).isFalse();
        assertThat(listed.etag()).isEqualTo(NEW_ETAG);
        assertThat(listed.issues()).extracting(GitHubIssue::number).containsExactly(1347);
        assertThat(github.requests()).singleElement().satisfies(request ->
                assertThat(request.header("If-None-Match")).isEqualTo(ETAG));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void listOpenIssues_blankEtag_sendsNoIfNoneMatch(String blankEtag) {
        givenToken();
        github.respond(exchange -> json(exchange, 200, "[]"));

        client.listOpenIssues(OWNER, NAME, blankEtag, null);

        assertThat(github.requests()).singleElement().satisfies(request ->
                assertThat(request.header("If-None-Match")).isNull());
    }

    @Test
    void listOpenIssues_notModifiedWithoutSendingEtag_failsAsInvalidResponse() {
        givenToken();
        github.respond(exchange -> notModified(exchange, ETAG));

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.INVALID_RESPONSE);
                    assertThat(e.getStatusCode()).hasValue(304);
                });
    }

    @Test
    void listOpenIssues_severalPages_followsNextLinksToTheLastPage() {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            etag(exchange, page == 1 ? NEW_ETAG : "W/\"page-" + page + "\"");
            links(exchange, page, 3);
            json(exchange, 200, switch (page) {
                case 1 -> issues(issue(11, "2026-09-28T01:00:00Z"), issue(12, "2026-09-28T02:00:00Z"));
                case 2 -> issues(issue(13, "2026-09-28T03:00:00Z"), issue(14, "2026-09-28T04:00:00Z"));
                default -> issues(issue(15, "2026-09-28T05:00:00Z"));
            });
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, ETAG, LocalDateTime.of(2026, 9, 28, 9, 0));

        assertThat(listed.notModified()).isFalse();
        assertThat(listed.complete()).isTrue();
        assertThat(listed.etag()).isEqualTo(NEW_ETAG);
        assertThat(listed.issues()).extracting(GitHubIssue::githubId).containsExactly(11L, 12L, 13L, 14L, 15L);
        assertThat(github.requests())
                .extracting(ReceivedRequest::rawPath)
                .containsExactly(ISSUES_PATH, ISSUES_BY_REPO_ID_PATH, ISSUES_BY_REPO_ID_PATH);
        assertThat(github.requests())
                .extracting(request -> request.queryParams().get("page"))
                .containsExactly(null, "2", "3");
        assertThat(github.requests())
                .extracting(request -> request.header("If-None-Match"))
                .containsExactly(ETAG, null, null);
        assertThat(github.requests())
                .extracting(request -> request.header("Authorization"))
                .containsOnly("Bearer " + TOKEN);
    }

    @Test
    void listOpenIssues_cursorNextLink_isSentAsGivenWithoutEncodingAgain() {
        givenToken();
        String cursorQuery = "state=open&sort=updated&direction=asc&since=2026-09-28T08%3A11%3A25Z&per_page=100"
                + "&after=Y3Vyc29yOnYyOpLPAAABkx5sXYjOd3Fsdg%3D%3D&page=2";
        github.respond(exchange -> {
            if (exchange.getRequestURI().getRawPath().equals(ISSUES_PATH)) {
                exchange.getResponseHeaders().set("Link",
                        "<" + github.url(ISSUES_BY_REPO_ID_PATH + "?" + cursorQuery) + ">; rel=\"next\"");
                json(exchange, 200, issues(issue(1, "2026-09-28T09:00:00Z")));
                return;
            }
            json(exchange, 200, issues(issue(2, "2026-09-28T10:00:00Z")));
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, null, LocalDateTime.of(2026, 9, 28, 17, 11, 25));

        assertThat(listed.issues()).extracting(GitHubIssue::githubId).containsExactly(1L, 2L);
        assertThat(github.requests()).hasSize(2);
        ReceivedRequest cursorPage = github.requests().get(1);
        assertThat(cursorPage.rawPath()).isEqualTo(ISSUES_BY_REPO_ID_PATH);
        assertThat(cursorPage.rawQuery()).isEqualTo(cursorQuery);
        assertThat(cursorPage.queryParams())
                .containsEntry("after", "Y3Vyc29yOnYyOpLPAAABkx5sXYjOd3Fsdg==")
                .containsEntry("since", "2026-09-28T08:11:25Z");
    }

    @Test
    void listOpenIssues_exactlyPageLimitPages_readsToTheEndAsComplete(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            etag(exchange, page == 1 ? NEW_ETAG : "W/\"page-" + page + "\"");
            links(exchange, page, RestGitHubClient.MAX_ISSUE_PAGES);
            json(exchange, 200, issues(issue(page, "2026-09-28T01:00:00Z")));
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, null, null);

        assertThat(listed.complete()).isTrue();
        assertThat(listed.etag()).isEqualTo(NEW_ETAG);
        assertThat(listed.issues()).hasSize(RestGitHubClient.MAX_ISSUE_PAGES);
        assertThat(github.requests()).hasSize(RestGitHubClient.MAX_ISSUE_PAGES);
        assertThat(output.getAll()).doesNotContain("이슈 목록이");
    }

    @Test
    void listOpenIssues_morePagesThanLimit_stopsAtLimitAsPartialWithoutEtag(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            etag(exchange, page == 1 ? NEW_ETAG : "W/\"page-" + page + "\"");
            links(exchange, page, RestGitHubClient.MAX_ISSUE_PAGES + 1);
            json(exchange, 200, issues(issue(page, "2026-09-28T01:00:00Z")));
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, null, null);

        assertThat(listed.notModified()).isFalse();
        assertThat(listed.complete()).isFalse();
        assertThat(listed.etag()).isNull();
        assertThat(listed.issues()).hasSize(RestGitHubClient.MAX_ISSUE_PAGES);
        assertThat(github.requests()).hasSize(RestGitHubClient.MAX_ISSUE_PAGES);
        assertThat(output.getAll().lines().filter(line -> line.contains("WARN") && line.contains("이슈 목록이")))
                .singleElement().asString()
                .contains(ISSUES_PATH);
    }

    @Test
    void listOpenIssues_nextLinkToOtherHost_refusesWithoutSendingToken() throws IOException {
        givenToken();
        MockGitHubServer otherHost = new MockGitHubServer("localhost");
        try {
            otherHost.respond(exchange -> json(exchange, 200, "[]"));
            github.respond(exchange -> {
                exchange.getResponseHeaders().set("Link",
                        "<" + otherHost.url(ISSUES_BY_REPO_ID_PATH + "?page=2") + ">; rel=\"next\"");
                json(exchange, 200, issues(issue(1, "2026-09-28T01:00:00Z")));
            });

            assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                    .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                        assertThat(e.getReason()).isEqualTo(Reason.INVALID_RESPONSE);
                        assertNoTokenIn(e);
                    });
            assertThat(github.requests()).hasSize(1);
            assertThat(otherHost.requests()).isEmpty();
        } finally {
            otherHost.stop();
        }
    }

    @Test
    void listOpenIssues_nextLinkToOtherHostOnSamePort_refusesWithoutSendingToken() {
        givenToken();
        int apiPort = URI.create(github.url("")).getPort();
        String otherHostSamePort = "http://localhost:" + apiPort + ISSUES_BY_REPO_ID_PATH + "?page=2";
        github.respond(exchange -> {
            exchange.getResponseHeaders().set("Link", "<" + otherHostSamePort + ">; rel=\"next\"");
            json(exchange, 200, issues(issue(1, "2026-09-28T01:00:00Z")));
        });

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.INVALID_RESPONSE);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(1);
    }

    @Test
    void listOpenIssues_sameIssueOnSeveralPages_keepsNewestByUpdateTimeAndLaterOneOnTie() {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            links(exchange, page, 2);
            json(exchange, 200, page == 1
                    ? issues(
                            issue(21, "newer first", "2026-09-28T04:00:00Z"),
                            issue(22, "older first", "2026-09-28T01:00:00Z"),
                            issue(23, "tie first", "2026-09-28T02:00:00Z"))
                    : issues(
                            issue(22, "newer later", "2026-09-28T03:00:00Z"),
                            issue(21, "older later", "2026-09-28T01:00:00Z"),
                            issue(23, "tie later", "2026-09-28T02:00:00Z")));
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, null, null);

        assertThat(listed.issues())
                .extracting(GitHubIssue::githubId, GitHubIssue::title, GitHubIssue::updatedAt)
                .containsExactlyInAnyOrder(
                        tuple(21L, "newer first", LocalDateTime.of(2026, 9, 28, 13, 0)),
                        tuple(22L, "newer later", LocalDateTime.of(2026, 9, 28, 12, 0)),
                        tuple(23L, "tie later", LocalDateTime.of(2026, 9, 28, 11, 0)));
    }

    @Test
    void listOpenIssues_pullRequestKey_marksOnlyPresentAndNonNullValueAsPullRequest() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, """
                [
                  {"id": 31, "number": 31, "title": "PR", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z",
                   "pull_request": {"url": "https://api.github.com/repos/octocat/Hello-World/pulls/31",
                                    "merged_at": null}},
                  {"id": 32, "number": 32, "title": "null PR", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z", "pull_request": null},
                  {"id": 33, "number": 33, "title": "no PR", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z"}
                ]
                """));

        assertThat(client.listOpenIssues(OWNER, NAME, null, null).issues())
                .extracting(GitHubIssue::githubId, GitHubIssue::pullRequest)
                .containsExactly(tuple(31L, true), tuple(32L, false), tuple(33L, false));
    }

    @Test
    void listOpenIssues_assignees_countsArrayLengthAndTreatsNullOrMissingAsZero() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, """
                [
                  {"id": 41, "number": 41, "title": "two", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z", "assignees": [{"login": "hubot"}, {"login": "octocat"}]},
                  {"id": 42, "number": 42, "title": "empty", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z", "assignees": []},
                  {"id": 43, "number": 43, "title": "null", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z", "assignees": null},
                  {"id": 44, "number": 44, "title": "missing", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z"}
                ]
                """));

        assertThat(client.listOpenIssues(OWNER, NAME, null, null).issues())
                .extracting(GitHubIssue::githubId, GitHubIssue::assigneeCount)
                .containsExactly(tuple(41L, 2), tuple(42L, 0), tuple(43L, 0), tuple(44L, 0));
    }

    @Test
    void listOpenIssues_authorAndBody_keepGitHubValuesAndNullWhenAbsent() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, """
                [
                  {"id": 51, "number": 51, "title": "ghost", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z", "user": null, "body": null},
                  {"id": 52, "number": 52, "title": "bare", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z"},
                  {"id": 53, "number": 53, "title": "bot", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z", "user": {"login": "dependabot[bot]", "type": "Bot"},
                   "body": ""},
                  {"id": 54, "number": 54, "title": "no type", "created_at": "2026-09-28T00:00:00Z",
                   "updated_at": "2026-09-28T00:00:00Z", "user": {"login": "octocat"}, "body": "text"}
                ]
                """));

        assertThat(client.listOpenIssues(OWNER, NAME, null, null).issues())
                .extracting(GitHubIssue::githubId, GitHubIssue::authorLogin, GitHubIssue::authorType, GitHubIssue::body)
                .containsExactly(
                        tuple(51L, null, null, null),
                        tuple(52L, null, null, null),
                        tuple(53L, "dependabot[bot]", "Bot", ""),
                        tuple(54L, "octocat", null, "text"));
    }

    @Test
    void listOpenIssues_rateLimit_isLoggedOncePerCallFromLastResponseWithoutQueryOrToken(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            rateLimit(exchange, RATE_LIMIT - page);
            links(exchange, page, 2);
            json(exchange, 200, issues(issue(page, "2026-09-28T01:00:00Z")));
        });

        client.listOpenIssues(OWNER, NAME, ETAG, LocalDateTime.of(2026, 9, 28, 17, 11, 25));

        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains("INFO")
                .contains("4998/5000")
                .contains(RATE_LIMIT_RESET_KST)
                .contains(ISSUES_PATH + " 상태 200")
                .doesNotContain("?")
                .doesNotContain("since")
                .doesNotContain("after=");
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("Bearer");
    }

    @ParameterizedTest
    @CsvSource({
            "501, INFO",
            "500, WARN",
            "0, WARN"
    })
    void listOpenIssues_rateLimitAtOrBelowTenPercent_isLoggedAsWarning(int remaining, String level,
                                                                       CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, remaining);
            json(exchange, 200, "[]");
        });

        client.listOpenIssues(OWNER, NAME, null, null);

        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains(level)
                .contains(remaining + "/" + RATE_LIMIT)
                .contains(RATE_LIMIT_RESET_KST);
    }

    @ParameterizedTest
    @CsvSource({
            "unlimited, 4999, 1790704800",
            "5000, plenty, 1790704800",
            "5000, 4999, soon"
    })
    void listOpenIssues_nonNumericRateLimitHeader_skipsOnlyTheLog(String limit, String remaining, String reset,
                                                                  CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            Headers headers = exchange.getResponseHeaders();
            headers.set("X-RateLimit-Limit", limit);
            headers.set("X-RateLimit-Remaining", remaining);
            headers.set("X-RateLimit-Reset", reset);
            etag(exchange, NEW_ETAG);
            json(exchange, 200, FOUND_A_BUG);
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, null, null);

        assertThat(listed.complete()).isTrue();
        assertThat(listed.etag()).isEqualTo(NEW_ETAG);
        assertThat(listed.issues()).extracting(GitHubIssue::number).containsExactly(1347);
        assertThat(rateLimitLines(output)).isEmpty();
    }

    @Test
    void listOpenIssues_rateLimitExceeded_failsAsRateLimitedAfterWarningWithResetTime(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, 0);
            json(exchange, 403, "{\"message\":\"API rate limit exceeded\"}");
        });

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, ETAG, null))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.RATE_LIMITED);
                    assertThat(e.getStatusCode()).hasValue(403);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(1);
        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains("WARN").contains("0/5000").contains(RATE_LIMIT_RESET_KST).contains("상태 403");
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429})
    void listOpenIssues_throttledWithRetryAfter_logsRetryAfterBeforeFailing(int status, CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, 4000);
            exchange.getResponseHeaders().set("Retry-After", "60");
            json(exchange, status, "{\"message\":\"You have exceeded a secondary rate limit.\"}");
        });

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.RATE_LIMITED);
                    assertThat(e.getStatusCode()).hasValue(status);
                });
        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains("4000/5000")
                .contains("상태 " + status + ", Retry-After 60");
    }

    @ParameterizedTest
    @CsvSource({
            "401, UNAUTHORIZED",
            "404, REJECTED",
            "422, REJECTED"
    })
    void listOpenIssues_clientError_logsRateLimitAndFailsWithReasonWithoutRetry(int status, Reason reason,
                                                                                CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, 4321);
            json(exchange, status, "{\"message\":\"client error\"}");
        });

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, ETAG, null))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(reason);
                    assertThat(e.getStatusCode()).hasValue(status);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(1);
        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains("4321/5000").contains("상태 " + status);
    }

    @Test
    void listOpenIssues_renamedRepo_followsRedirectWithEtagAndToken() {
        givenToken();
        github.respond(exchange -> {
            if (exchange.getRequestURI().getPath().equals(ISSUES_PATH)) {
                redirect(exchange, 301,
                        github.url(ISSUES_BY_REPO_ID_PATH + "?" + exchange.getRequestURI().getRawQuery()));
                return;
            }
            notModified(exchange, ETAG);
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, ETAG, LocalDateTime.of(2026, 9, 28, 17, 11, 25));

        assertThat(listed).isEqualTo(GitHubIssueListResult.unchanged(ETAG));
        assertThat(github.requests())
                .extracting(ReceivedRequest::rawPath)
                .containsExactly(ISSUES_PATH, ISSUES_BY_REPO_ID_PATH);
        assertThat(github.requests().get(1).queryParams())
                .containsAllEntriesOf(OPEN_ISSUES_BY_UPDATE)
                .containsEntry("since", "2026-09-28T08:11:25Z");
        assertThat(github.requests())
                .extracting(request -> request.header("If-None-Match"))
                .containsExactly(ETAG, ETAG);
        assertThat(github.requests())
                .extracting(request -> request.header("Authorization"))
                .containsOnly("Bearer " + TOKEN);
    }

    @Test
    void listOpenIssues_serverErrorOnLaterPage_retriesThatPageOnly() {
        givenToken();
        AtomicInteger secondPageCalls = new AtomicInteger();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            if (page == 2 && secondPageCalls.incrementAndGet() == 1) {
                json(exchange, 502, "{\"message\":\"Server Error\"}");
                return;
            }
            links(exchange, page, 2);
            json(exchange, 200, issues(issue(page, "2026-09-28T0" + page + ":00:00Z")));
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, null, null);

        assertThat(listed.complete()).isTrue();
        assertThat(listed.issues()).extracting(GitHubIssue::githubId).containsExactly(1L, 2L);
        assertThat(github.requests())
                .extracting(request -> request.queryParams().get("page"))
                .containsExactly(null, "2", "2");
    }

    @Test
    void listOpenIssues_laterPageKeepsFailingWithServerError_returnsPagesReadSoFarAsPartial(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            if (page == 2) {
                json(exchange, 503, "{\"message\":\"Service Unavailable\"}");
                return;
            }
            etag(exchange, NEW_ETAG);
            links(exchange, page, 3);
            json(exchange, 200, issues(issue(page, "2026-09-28T01:00:00Z")));
        });

        GitHubIssueListResult listed = client.listOpenIssues(OWNER, NAME, null, null);

        assertThat(listed.notModified()).isFalse();
        assertThat(listed.complete()).isFalse();
        assertThat(listed.etag()).isNull();
        assertThat(listed.issues()).extracting(GitHubIssue::githubId).containsExactly(1L);
        assertThat(github.requests()).hasSize(1 + MAX_ATTEMPTS);
        assertThat(output.getAll().lines().filter(line -> line.contains("WARN") && line.contains("끝내 받지 못해")))
                .singleElement().asString()
                .contains("2페이지")
                .contains(ISSUES_PATH);
    }

    @Test
    void listOpenIssues_laterPageKeepsTimingOut_returnsPagesReadSoFarAsPartial() {
        givenToken();
        RestGitHubClient impatientClient = clientWithReadTimeout(SHORT_READ_TIMEOUT);
        github.respond(exchange -> {
            int page = pageOf(exchange);
            if (page == 2) {
                MockGitHubServer.stall(exchange);
                return;
            }
            links(exchange, page, 2);
            json(exchange, 200, issues(issue(page, "2026-09-28T01:00:00Z")));
        });

        GitHubIssueListResult listed = impatientClient.listOpenIssues(OWNER, NAME, null, null);

        assertThat(listed.complete()).isFalse();
        assertThat(listed.etag()).isNull();
        assertThat(listed.issues()).extracting(GitHubIssue::githubId).containsExactly(1L);
        assertThat(github.requests()).hasSize(1 + MAX_ATTEMPTS);
    }

    @ParameterizedTest
    @CsvSource({
            "401, UNAUTHORIZED",
            "403, RATE_LIMITED",
            "429, RATE_LIMITED",
            "404, REJECTED",
            "422, REJECTED"
    })
    void listOpenIssues_laterPageRejected_failsWithoutPartialResult(int status, Reason reason) {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            if (page == 2) {
                json(exchange, status, "{\"message\":\"rejected\"}");
                return;
            }
            links(exchange, page, 2);
            json(exchange, 200, issues(issue(page, "2026-09-28T01:00:00Z")));
        });

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(reason);
                    assertThat(e.getStatusCode()).hasValue(status);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(2);
    }

    @Test
    void listOpenIssues_laterPageUnreadable_failsAsInvalidResponse() {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            if (page == 2) {
                json(exchange, 200, "not a json");
                return;
            }
            links(exchange, page, 2);
            json(exchange, 200, issues(issue(page, "2026-09-28T01:00:00Z")));
        });

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                .isInstanceOfSatisfying(GitHubClientException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID_RESPONSE));
        assertThat(github.requests()).hasSize(2);
    }

    @Test
    void listOpenIssues_laterPageRedirectsOutsideApi_failsAsRedirectRefused() {
        givenToken();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            if (page == 2) {
                redirect(exchange, 302, "https://elsewhere.example.com/issues");
                return;
            }
            links(exchange, page, 2);
            json(exchange, 200, issues(issue(page, "2026-09-28T01:00:00Z")));
        });

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.REDIRECT_REFUSED);
                    assertThat(e.getStatusCode()).hasValue(302);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(2);
    }

    @Test
    void listOpenIssues_firstPageKeepsFailing_failsAsUnavailableAfterLoggingRateLimit(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> {
            rateLimit(exchange, 4500);
            json(exchange, 502, "{\"message\":\"Server Error\"}");
        });

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(MAX_ATTEMPTS);
        assertThat(rateLimitLines(output)).singleElement().asString()
                .contains("4500/5000").contains("상태 502");
    }

    @Test
    void listOpenIssues_interruptedWhileReadingLaterPage_failsKeepingInterruptInsteadOfPartial() {
        givenToken();
        Thread caller = Thread.currentThread();
        github.respond(exchange -> {
            int page = pageOf(exchange);
            if (page == 2) {
                caller.interrupt();
                json(exchange, 503, "{\"message\":\"Service Unavailable\"}");
                return;
            }
            links(exchange, page, 2);
            json(exchange, 200, issues(issue(page, "2026-09-28T01:00:00Z")));
        });

        try {
            assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                    .isInstanceOfSatisfying(GitHubClientException.class,
                            e -> assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    static Stream<String> unreadableIssuePages() {
        String createdAt = "\"created_at\": \"2026-09-28T00:00:00Z\"";
        String updatedAt = "\"updated_at\": \"2026-09-28T00:00:00Z\"";
        return Stream.of(
                "not a json",
                "{}",
                "null",
                "[null]",
                "[{\"number\": 1, \"title\": \"t\", " + createdAt + ", " + updatedAt + "}]",
                "[{\"id\": 1, \"title\": \"t\", " + createdAt + ", " + updatedAt + "}]",
                "[{\"id\": 1, \"number\": 1, " + createdAt + ", " + updatedAt + "}]",
                "[{\"id\": 1, \"number\": 1, \"title\": \"t\", " + updatedAt + "}]",
                "[{\"id\": 1, \"number\": 1, \"title\": \"t\", " + createdAt + "}]",
                "[{\"id\": 1, \"number\": 1, \"title\": \"t\", \"created_at\": \"2026-09-28 00:00:00\", "
                        + updatedAt + "}]");
    }

    @ParameterizedTest
    @MethodSource("unreadableIssuePages")
    void listOpenIssues_unreadableIssuePage_failsAsInvalidResponseWithoutRetry(String body) {
        givenToken();
        github.respond(exchange -> json(exchange, 200, body));

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, null, null))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.INVALID_RESPONSE);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(1);
    }

    @Test
    void listOpenIssues_withoutToken_sendsNoRequest() {
        given(credentials.isConfigured()).willReturn(false);

        assertThatThrownBy(() -> client.listOpenIssues(OWNER, NAME, ETAG, null))
                .isInstanceOfSatisfying(GitHubCredentialsMissingException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.NOT_CONFIGURED));
        assertThat(github.requests()).isEmpty();
        then(credentials).should(never()).authorizationHeader();
    }

    private RestGitHubClient clientWithReadTimeout(Duration readTimeout) {
        return new RestGitHubClient(RestClient.builder(), credentials, github.url(""), CONNECT_TIMEOUT,
                readTimeout, MAX_ATTEMPTS, RETRY_BACKOFF);
    }

    private void givenToken() {
        given(credentials.isConfigured()).willReturn(true);
        given(credentials.authorizationHeader()).willReturn("Bearer " + TOKEN);
    }

    private void links(HttpExchange exchange, int page, int lastPage) {
        String query = exchange.getRequestURI().getRawQuery().replaceAll("&(after|page)=[^&]*", "");
        List<String> links = new ArrayList<>();
        if (page > 1) {
            links.add(link(query, page - 1, "prev"));
        }
        if (page < lastPage) {
            links.add(link(query, page + 1, "next"));
        }
        if (!links.isEmpty()) {
            exchange.getResponseHeaders().set("Link", String.join(", ", links));
        }
    }

    private String link(String query, int page, String relation) {
        return "<" + github.url(ISSUES_BY_REPO_ID_PATH + "?" + query + "&after=" + cursor(page) + "&page=" + page)
                + ">; rel=\"" + relation + "\"";
    }

    private static String cursor(int page) {
        String cursor = Base64.getEncoder().encodeToString(("cursor:v2:" + page).getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(cursor, StandardCharsets.UTF_8);
    }

    private static int pageOf(HttpExchange exchange) {
        return Integer.parseInt(MockGitHubServer.queryParams(exchange.getRequestURI().getRawQuery())
                .getOrDefault("page", "1"));
    }

    private static void etag(HttpExchange exchange, String etag) {
        exchange.getResponseHeaders().set("ETag", etag);
    }

    private static void rateLimit(HttpExchange exchange, long remaining) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("X-RateLimit-Limit", String.valueOf(RATE_LIMIT));
        headers.set("X-RateLimit-Remaining", String.valueOf(remaining));
        headers.set("X-RateLimit-Used", String.valueOf(RATE_LIMIT - remaining));
        headers.set("X-RateLimit-Reset", String.valueOf(RATE_LIMIT_RESET));
        headers.set("X-RateLimit-Resource", "core");
    }

    private static void notModified(HttpExchange exchange, String etag) throws IOException {
        etag(exchange, etag);
        exchange.sendResponseHeaders(304, -1);
        exchange.close();
    }

    private static String issues(String... issues) {
        return "[" + String.join(",", issues) + "]";
    }

    private static String issue(long id, String updatedAt) {
        return issue(id, "Issue " + id, updatedAt);
    }

    private static String issue(long id, String title, String updatedAt) {
        return """
                {"id": %d, "number": %d, "title": "%s", "body": "Steps to reproduce",
                 "user": {"login": "octocat", "type": "User"}, "assignees": [],
                 "created_at": "2026-09-27T00:00:00Z", "updated_at": "%s"}
                """.formatted(id, id, title, updatedAt);
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
