package uhsuhjupjup.backend.oss.github.infra;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import uhsuhjupjup.backend.oss.github.application.dto.GitHubRepo;
import uhsuhjupjup.backend.oss.github.infra.MockGitHubServer.ReceivedRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static uhsuhjupjup.backend.oss.github.infra.MockGitHubServer.json;
import static uhsuhjupjup.backend.oss.github.infra.MockGitHubServer.redirect;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RestGitHubClientTest {

    private static final String TOKEN = "ghp_restClientToken";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SHORT_READ_TIMEOUT = Duration.ofMillis(300);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(10);
    private static final String REPO_JSON = """
            {
              "id": 1296269,
              "name": "Hello-World",
              "full_name": "octocat/Hello-World",
              "owner": {"login": "octocat", "id": 1},
              "private": false,
              "description": "This your first repo!",
              "language": "Java",
              "stargazers_count": 80,
              "has_issues": true,
              "archived": false,
              "topics": ["octocat", "api"]
            }
            """;

    @Mock
    private GitHubCredentials credentials;

    private MockGitHubServer github;
    private RestGitHubClient client;

    @BeforeEach
    void setUp() throws IOException {
        github = new MockGitHubServer("127.0.0.1");
        client = clientFor(RestClient.builder(), github.url(""), READ_TIMEOUT, RETRY_BACKOFF);
    }

    @AfterEach
    void tearDown() {
        github.stop();
    }

    @Test
    void findRepo_ok_mapsRepoAndSendsGitHubHeaders() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, REPO_JSON));

        assertThat(client.findRepo("octocat", "Hello-World")).contains(new GitHubRepo(
                1296269L, "octocat/Hello-World", "This your first repo!", "Java", 80, false, true, false));

        assertThat(github.requests()).hasSize(1);
        ReceivedRequest request = github.requests().get(0);
        assertThat(request.rawPath()).isEqualTo("/repos/octocat/Hello-World");
        assertThat(request.header("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(request.header("Accept")).isEqualTo("application/vnd.github+json");
        assertThat(request.header("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
        assertThat(request.header("User-Agent")).isEqualTo("UhsuhJupJup-OSS/1.0 (+https://www.uhsuh.com)");
    }

    @Test
    void findRepo_withoutDescriptionAndLanguage_keepsThemNull() {
        givenToken();
        github.respond(exchange -> json(exchange, 200, """
                {"id": 7, "full_name": "octocat/empty", "description": null, "language": null,
                 "stargazers_count": 0, "private": true, "has_issues": false, "archived": true}
                """));

        assertThat(client.findRepo("octocat", "empty"))
                .contains(new GitHubRepo(7L, "octocat/empty", null, null, 0, true, false, true));
    }

    @Test
    void findRepo_specialCharactersInOwnerOrName_areEncodedIntoPath() {
        givenToken();
        github.respond(exchange -> json(exchange, 404, "{\"message\":\"Not Found\"}"));

        assertThat(client.findRepo("octo/cat", "a b?c#d")).isEmpty();

        assertThat(github.requests())
                .extracting(ReceivedRequest::rawPath)
                .containsExactly("/repos/octo%2Fcat/a%20b%3Fc%23d");
    }

    @Test
    void findRepo_notFound_returnsEmptyWithoutRetry() {
        givenToken();
        github.respond(exchange -> json(exchange, 404, "{\"message\":\"Not Found\"}"));

        assertThat(client.findRepo("octocat", "missing")).isEmpty();
        assertThat(github.requests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void findRepo_redirectToSameHost_followsWithAuthorization(int status) {
        givenToken();
        github.respond(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/repos/octocat/old-name")) {
                redirect(exchange, status, github.url("/repositories/1296269"));
                return;
            }
            json(exchange, 200, REPO_JSON);
        });

        assertThat(client.findRepo("octocat", "old-name"))
                .map(GitHubRepo::fullName)
                .contains("octocat/Hello-World");
        assertThat(github.requests())
                .extracting(ReceivedRequest::rawPath)
                .containsExactly("/repos/octocat/old-name", "/repositories/1296269");
        assertThat(github.requests())
                .extracting(request -> request.header("Authorization"))
                .containsExactly("Bearer " + TOKEN, "Bearer " + TOKEN);
    }

    @Test
    void findRepo_redirectToOtherHost_refusesWithoutSendingToken() throws IOException {
        givenToken();
        MockGitHubServer otherHost = new MockGitHubServer("localhost");
        try {
            otherHost.respond(exchange -> json(exchange, 200, REPO_JSON));
            github.respond(exchange -> redirect(exchange, 301, otherHost.url("/repositories/1296269")));

            assertThatThrownBy(() -> client.findRepo("octocat", "old-name"))
                    .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                        assertThat(e.getReason()).isEqualTo(Reason.REDIRECT_REFUSED);
                        assertThat(e.getStatusCode()).hasValue(301);
                        assertNoTokenIn(e);
                    });
            assertThat(github.requests()).hasSize(1);
            assertThat(otherHost.requests()).isEmpty();
        } finally {
            otherHost.stop();
        }
    }

    @Test
    void findRepo_redirectLoop_stopsAtRedirectLimit() {
        givenToken();
        github.respond(exchange -> redirect(exchange, 301, github.url("/repositories/1296269")));

        assertThatThrownBy(() -> client.findRepo("octocat", "loop"))
                .isInstanceOfSatisfying(GitHubClientException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.REDIRECT_REFUSED));
        assertThat(github.requests()).hasSize(1 + RestGitHubClient.MAX_REDIRECTS);
    }

    @Test
    void findRepo_readTimeout_triesMaxAttemptsThenReportsUnavailable(CapturedOutput output) {
        givenToken();
        github.respond(MockGitHubServer::stall);
        RestGitHubClient impatientClient =
                clientFor(RestClient.builder(), github.url(""), SHORT_READ_TIMEOUT, RETRY_BACKOFF);

        assertThatThrownBy(() -> impatientClient.findRepo("octocat", "Hello-World"))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                    assertThat(e.getStatusCode()).isEmpty();
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(MAX_ATTEMPTS);
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("Bearer");
    }

    @Test
    void findRepo_connectionRefused_triesMaxAttemptsThenReportsUnavailable() throws IOException {
        givenToken();
        AtomicInteger attempts = new AtomicInteger();
        RestClient.Builder countingBuilder = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    attempts.incrementAndGet();
                    return execution.execute(request, body);
                });
        RestGitHubClient unreachableClient =
                clientFor(countingBuilder, "http://127.0.0.1:" + closedPort(), READ_TIMEOUT, RETRY_BACKOFF);

        assertThatThrownBy(() -> unreachableClient.findRepo("octocat", "Hello-World"))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                    assertThat(e.getStatusCode()).isEmpty();
                    assertNoTokenIn(e);
                });
        assertThat(attempts).hasValue(MAX_ATTEMPTS);
    }

    @Test
    void findRepo_serverErrorEveryTime_triesMaxAttemptsThenReportsUnavailable(CapturedOutput output) {
        givenToken();
        github.respond(exchange -> json(exchange, 502, "{\"message\":\"Server Error\"}"));

        assertThatThrownBy(() -> client.findRepo("octocat", "Hello-World"))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE);
                    assertThat(e.getStatusCode()).isEmpty();
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(MAX_ATTEMPTS);
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("Bearer");
    }

    @Test
    void findRepo_serverErrorThenOk_waitsBackoffAndReturnsRepo() {
        givenToken();
        Duration backoff = Duration.ofMillis(200);
        AtomicInteger calls = new AtomicInteger();
        github.respond(exchange -> {
            if (calls.incrementAndGet() == 1) {
                json(exchange, 503, "{\"message\":\"Service Unavailable\"}");
                return;
            }
            json(exchange, 200, REPO_JSON);
        });
        RestGitHubClient patientClient = clientFor(RestClient.builder(), github.url(""), READ_TIMEOUT, backoff);

        long startedAt = System.nanoTime();
        assertThat(patientClient.findRepo("octocat", "Hello-World")).isPresent();

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThanOrEqualTo(backoff);
        assertThat(github.requests()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({
            "401, UNAUTHORIZED",
            "403, RATE_LIMITED",
            "429, RATE_LIMITED",
            "400, REJECTED",
            "422, REJECTED"
    })
    void findRepo_clientError_failsWithReasonAndStatusWithoutRetry(int status, Reason reason) {
        givenToken();
        github.respond(exchange -> json(exchange, status, "{\"message\":\"client error\"}"));

        assertThatThrownBy(() -> client.findRepo("octocat", "Hello-World"))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(reason);
                    assertThat(e.getStatusCode()).hasValue(status);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a json", "{}", "null"})
    void findRepo_unreadableBody_failsAsInvalidResponseWithoutRetry(String body) {
        givenToken();
        github.respond(exchange -> json(exchange, 200, body));

        assertThatThrownBy(() -> client.findRepo("octocat", "Hello-World"))
                .isInstanceOfSatisfying(GitHubClientException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.INVALID_RESPONSE);
                    assertNoTokenIn(e);
                });
        assertThat(github.requests()).hasSize(1);
    }

    @Test
    void findRepo_withoutToken_sendsNoRequest() {
        given(credentials.isConfigured()).willReturn(false);

        assertThatThrownBy(() -> client.findRepo("octocat", "Hello-World"))
                .isInstanceOfSatisfying(GitHubCredentialsMissingException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.NOT_CONFIGURED);
                    assertThat(e.getStatusCode()).isEmpty();
                });
        assertThat(github.requests()).isEmpty();
        then(credentials).should(never()).authorizationHeader();
    }

    @Test
    void findRepo_readsAuthorizationHeaderForEveryRequest() {
        given(credentials.isConfigured()).willReturn(true);
        given(credentials.authorizationHeader()).willReturn("Bearer first", "Bearer second");
        github.respond(exchange -> json(exchange, 200, REPO_JSON));

        client.findRepo("octocat", "Hello-World");
        client.findRepo("octocat", "Hello-World");

        assertThat(github.requests())
                .extracting(request -> request.header("Authorization"))
                .containsExactly("Bearer first", "Bearer second");
    }

    private RestGitHubClient clientFor(RestClient.Builder builder, String apiBaseUrl, Duration readTimeout,
                                       Duration retryBackoff) {
        return new RestGitHubClient(builder, credentials, apiBaseUrl, CONNECT_TIMEOUT, readTimeout, MAX_ATTEMPTS,
                retryBackoff);
    }

    private void givenToken() {
        given(credentials.isConfigured()).willReturn(true);
        given(credentials.authorizationHeader()).willReturn("Bearer " + TOKEN);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void assertNoTokenIn(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            assertThat(cause.toString()).doesNotContain(TOKEN).doesNotContain("Bearer");
        }
    }
}
