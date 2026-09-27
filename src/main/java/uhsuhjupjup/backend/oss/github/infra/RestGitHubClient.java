package uhsuhjupjup.backend.oss.github.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentials;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentialsMissingException;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubRepo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
class RestGitHubClient implements GitHubClient {

    static final int MAX_REDIRECTS = 3;

    private static final String GITHUB_JSON = "application/vnd.github+json";
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    private static final String API_VERSION = "2022-11-28";
    private static final String USER_AGENT = "UhsuhJupJup-OSS/1.0 (+https://www.uhsuh.com)";
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(
            HttpStatus.MOVED_PERMANENTLY.value(),
            HttpStatus.FOUND.value(),
            HttpStatus.SEE_OTHER.value(),
            HttpStatus.TEMPORARY_REDIRECT.value(),
            HttpStatus.PERMANENT_REDIRECT.value());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final GitHubCredentials credentials;
    private final URI apiBaseUri;
    private final int maxAttempts;
    private final Duration retryBackoff;

    RestGitHubClient(RestClient.Builder restClientBuilder,
                     GitHubCredentials credentials,
                     @Value("${oss.github.api-base-url:https://api.github.com}") String apiBaseUrl,
                     @Value("${oss.github.connect-timeout:PT3S}") Duration connectTimeout,
                     @Value("${oss.github.read-timeout:PT10S}") Duration readTimeout,
                     @Value("${oss.github.max-attempts:3}") int maxAttempts,
                     @Value("${oss.github.retry-backoff:PT1S}") Duration retryBackoff) {
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .defaultHeader(HttpHeaders.ACCEPT, GITHUB_JSON)
                .defaultHeader(API_VERSION_HEADER, API_VERSION)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
        this.credentials = credentials;
        this.apiBaseUri = URI.create(apiBaseUrl);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoff = retryBackoff;
    }

    private static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }

    @Override
    public Optional<GitHubRepo> findRepo(String owner, String name) {
        if (!credentials.isConfigured()) {
            throw new GitHubCredentialsMissingException();
        }
        URI uri = UriComponentsBuilder.fromUri(apiBaseUri)
                .pathSegment("repos", owner, name)
                .build()
                .encode()
                .toUri();
        GitHubResponse response = sendWithRetry(uri);
        for (int redirects = 0; response.isRedirect(); redirects++) {
            if (redirects == MAX_REDIRECTS) {
                throw redirectRefused(response,
                        "GitHub 리다이렉트가 " + MAX_REDIRECTS + "번을 넘었습니다: " + uri.getPath(), null);
            }
            uri = redirectTarget(uri, response);
            response = sendWithRetry(uri);
        }
        return readRepo(uri, response);
    }

    private GitHubResponse sendWithRetry(URI uri) {
        for (int attempt = 1; ; attempt++) {
            try {
                GitHubResponse response = send(uri);
                if (!response.status().is5xxServerError()) {
                    return response;
                }
                backOffOrGiveUp(uri, attempt, "상태 " + response.status().value(), null);
            } catch (ResourceAccessException e) {
                backOffOrGiveUp(uri, attempt, e.getMostSpecificCause().getClass().getSimpleName(), e);
            }
        }
    }

    private void backOffOrGiveUp(URI uri, int attempt, String failure, ResourceAccessException cause) {
        if (attempt >= maxAttempts) {
            throw new GitHubClientException(Reason.UNAVAILABLE,
                    "GitHub가 응답하지 않습니다(시도 " + attempt + "번, 마지막 " + failure + "): " + uri.getPath(), cause);
        }
        log.warn("GitHub 요청 실패, {}ms 뒤 다시 시도 {}/{}: {} ({})",
                retryBackoff.toMillis(), attempt + 1, maxAttempts, uri.getPath(), failure);
        try {
            Thread.sleep(retryBackoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubClientException(Reason.UNAVAILABLE,
                    "GitHub 재시도를 기다리다 중단됐습니다: " + uri.getPath(), e);
        }
    }

    private GitHubResponse send(URI uri) {
        return restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, credentials.authorizationHeader())
                .exchange((request, response) -> new GitHubResponse(
                        response.getStatusCode(),
                        response.getHeaders().getFirst(HttpHeaders.LOCATION),
                        StreamUtils.copyToByteArray(response.getBody())));
    }

    private URI redirectTarget(URI current, GitHubResponse response) {
        String location = response.location();
        if (location == null || location.isBlank()) {
            throw redirectRefused(response, "GitHub 리다이렉트 응답에 Location이 없습니다: " + current.getPath(), null);
        }
        URI target;
        try {
            target = current.resolve(location.strip());
        } catch (IllegalArgumentException e) {
            throw redirectRefused(response, "GitHub 리다이렉트 위치를 해석하지 못했습니다: " + current.getPath(), e);
        }
        if (!isApiOrigin(target)) {
            throw redirectRefused(response, "GitHub API 밖으로 가는 리다이렉트는 따라가지 않습니다: " + target.getHost(), null);
        }
        return target;
    }

    private boolean isApiOrigin(URI target) {
        return apiBaseUri.getScheme().equalsIgnoreCase(target.getScheme())
                && apiBaseUri.getHost().equalsIgnoreCase(target.getHost())
                && port(apiBaseUri) == port(target);
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private Optional<GitHubRepo> readRepo(URI uri, GitHubResponse response) {
        HttpStatusCode status = response.status();
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return Optional.empty();
        }
        if (!status.isSameCodeAs(HttpStatus.OK)) {
            throw new GitHubClientException(reasonFor(status), status.value(),
                    "GitHub 요청 실패(상태 " + status.value() + "): " + uri.getPath(), null);
        }
        return Optional.of(parseRepo(uri, response.body()));
    }

    private static Reason reasonFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> Reason.UNAUTHORIZED;
            case 403, 429 -> Reason.RATE_LIMITED;
            default -> status.is4xxClientError() ? Reason.REJECTED : Reason.INVALID_RESPONSE;
        };
    }

    private GitHubRepo parseRepo(URI uri, byte[] body) {
        RepoPayload payload;
        try {
            payload = OBJECT_MAPPER.readValue(body, RepoPayload.class);
        } catch (IOException e) {
            throw invalidResponse("GitHub 레포 응답을 읽지 못했습니다: " + uri.getPath(), e);
        }
        if (payload == null || payload.id() == null || payload.fullName() == null) {
            throw invalidResponse("GitHub 레포 응답에 id나 full_name이 없습니다: " + uri.getPath(), null);
        }
        return new GitHubRepo(payload.id(), payload.fullName(), payload.description(), payload.language(),
                payload.stargazersCount(), payload.privateRepo(), payload.hasIssues(), payload.archived());
    }

    private static GitHubClientException redirectRefused(GitHubResponse response, String message, Throwable cause) {
        return new GitHubClientException(Reason.REDIRECT_REFUSED, response.status().value(), message, cause);
    }

    private static GitHubClientException invalidResponse(String message, Throwable cause) {
        return new GitHubClientException(Reason.INVALID_RESPONSE, HttpStatus.OK.value(), message, cause);
    }

    private record GitHubResponse(HttpStatusCode status, String location, byte[] body) {

        boolean isRedirect() {
            return REDIRECT_STATUSES.contains(status.value());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RepoPayload(
            Long id,
            @JsonProperty("full_name") String fullName,
            String description,
            String language,
            @JsonProperty("stargazers_count") int stargazersCount,
            @JsonProperty("private") boolean privateRepo,
            @JsonProperty("has_issues") boolean hasIssues,
            boolean archived) {
    }
}
