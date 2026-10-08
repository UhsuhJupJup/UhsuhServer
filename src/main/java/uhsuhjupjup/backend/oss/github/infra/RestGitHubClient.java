package uhsuhjupjup.backend.oss.github.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
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
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssue;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueDetail;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueListResult;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueListResult.IncompleteReason;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueLookup;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubRepo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
class RestGitHubClient implements GitHubClient {

    static final int MAX_REDIRECTS = 3;
    static final int MAX_ISSUE_PAGES = 10;
    static final int ISSUES_PER_PAGE = 100;

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
    private static final Set<Integer> THROTTLED_STATUSES = Set.of(
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.TOO_MANY_REQUESTS.value());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern LINK_ENTRY = Pattern.compile("<([^>]*)>\\s*;\\s*rel=\"([^\"]*)\"");
    private static final String NEXT_PAGE_RELATION = "next";
    private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";
    private static final int LOW_RATE_LIMIT_PERCENT = 10;
    private static final String OPEN_STATE = "open";
    private static final String LABEL_NAME_FIELD = "name";
    private static final Consumer<GitHubResponse> IGNORE_RESPONSE = response -> {
    };

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
        requireCredentials();
        URI uri = UriComponentsBuilder.fromUri(apiBaseUri)
                .pathSegment("repos", owner, name)
                .build()
                .encode()
                .toUri();
        return readRepo(sendFollowingRedirects(uri, null, IGNORE_RESPONSE));
    }

    @Override
    public GitHubIssueListResult listOpenIssues(String owner, String name, String previousEtag,
                                                LocalDateTime updatedSince) {
        requireCredentials();
        URI firstPageUri = openIssuesUri(owner, name, updatedSince);
        String etag = previousEtag == null || previousEtag.isBlank() ? null : previousEtag;
        AtomicReference<RateLimit> lastRateLimit = new AtomicReference<>();
        try {
            return readOpenIssues(firstPageUri, etag,
                    response -> RateLimit.of(response).ifPresent(lastRateLimit::set));
        } finally {
            logRateLimit(firstPageUri, lastRateLimit.get());
        }
    }

    @Override
    public GitHubIssueLookup findIssue(long repositoryId, int number) {
        requireCredentials();
        URI uri = UriComponentsBuilder.fromUri(apiBaseUri)
                .pathSegment("repositories", String.valueOf(repositoryId), "issues", String.valueOf(number))
                .build()
                .encode()
                .toUri();
        AtomicReference<RateLimit> lastRateLimit = new AtomicReference<>();
        try {
            return readIssue(sendWithRetry(uri, null,
                    response -> RateLimit.of(response).ifPresent(lastRateLimit::set)));
        } finally {
            logRateLimit(uri, lastRateLimit.get());
        }
    }

    private void requireCredentials() {
        if (!credentials.isConfigured()) {
            throw new GitHubCredentialsMissingException();
        }
    }

    private URI openIssuesUri(String owner, String name, LocalDateTime updatedSince) {
        return UriComponentsBuilder.fromUri(apiBaseUri)
                .pathSegment("repos", owner, name, "issues")
                .queryParam("state", "open")
                .queryParam("sort", "updated")
                .queryParam("direction", "asc")
                .queryParam("per_page", ISSUES_PER_PAGE)
                .queryParamIfPresent("since", Optional.ofNullable(updatedSince).map(RestGitHubClient::toGitHubTime))
                .build()
                .encode()
                .toUri();
    }

    private GitHubIssueListResult readOpenIssues(URI firstPageUri, String etag, Consumer<GitHubResponse> received) {
        GitHubResponse firstPage = sendFollowingRedirects(firstPageUri, etag, received);
        if (etag != null && firstPage.isNotModified()) {
            if (nextPageLink(firstPage).isEmpty()) {
                return GitHubIssueListResult.unchanged(etag);
            }
            firstPage = sendFollowingRedirects(firstPage.uri(), null, received);
        }
        Map<Long, GitHubIssue> issues = new LinkedHashMap<>();
        GitHubResponse page = firstPage;
        for (int pageNumber = 1; ; pageNumber++) {
            List<GitHubIssue> pageIssues = readIssues(page);
            pageIssues.forEach(issue -> keepLatest(issues, issue));
            Optional<URI> nextPage = nextPageUri(page);
            if (nextPage.isEmpty()) {
                return GitHubIssueListResult.changed(etagCoveringWholeList(firstPage, pageNumber, pageIssues.size()),
                        List.copyOf(issues.values()));
            }
            if (pageNumber == MAX_ISSUE_PAGES) {
                log.warn("GitHub 이슈 목록이 {}페이지를 넘어 이번에는 {}건까지만 읽었습니다: {}",
                        MAX_ISSUE_PAGES, issues.size(), firstPageUri.getPath());
                return GitHubIssueListResult.partial(List.copyOf(issues.values()), IncompleteReason.PAGE_LIMIT);
            }
            try {
                page = sendFollowingRedirects(nextPage.get(), null, received);
            } catch (GitHubClientException e) {
                if (!gaveUpRetrying(e)) {
                    throw e;
                }
                log.warn("GitHub 이슈 목록 {}페이지를 끝내 받지 못해 앞의 {}건만 돌려줍니다: {} ({})",
                        pageNumber + 1, issues.size(), firstPageUri.getPath(), e.getMessage());
                return GitHubIssueListResult.partial(List.copyOf(issues.values()), IncompleteReason.PAGE_UNAVAILABLE);
            }
        }
    }

    private static String etagCoveringWholeList(GitHubResponse firstPage, int pages, int issuesOnLastPage) {
        if (pages == 1 && issuesOnLastPage < ISSUES_PER_PAGE) {
            return firstPage.etag();
        }
        return null;
    }

    private static boolean gaveUpRetrying(GitHubClientException e) {
        return e.getReason() == Reason.UNAVAILABLE && !Thread.currentThread().isInterrupted();
    }

    private GitHubResponse sendFollowingRedirects(URI uri, String etag, Consumer<GitHubResponse> received) {
        GitHubResponse response = sendWithRetry(uri, etag, received);
        for (int redirects = 0; response.isRedirect(); redirects++) {
            if (redirects == MAX_REDIRECTS) {
                throw redirectRefused(response,
                        "GitHub 리다이렉트가 " + MAX_REDIRECTS + "번을 넘었습니다: " + response.path(), null);
            }
            response = sendWithRetry(redirectTarget(response), etag, received);
        }
        return response;
    }

    private GitHubResponse sendWithRetry(URI uri, String etag, Consumer<GitHubResponse> received) {
        for (int attempt = 1; ; attempt++) {
            try {
                GitHubResponse response = send(uri, etag);
                received.accept(response);
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

    private GitHubResponse send(URI uri, String etag) {
        return restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, credentials.authorizationHeader())
                .headers(headers -> {
                    if (etag != null) {
                        headers.setIfNoneMatch(etag);
                    }
                })
                .exchange((request, response) -> new GitHubResponse(
                        uri,
                        response.getStatusCode(),
                        response.getHeaders(),
                        StreamUtils.copyToByteArray(response.getBody())));
    }

    private URI redirectTarget(GitHubResponse response) {
        String location = response.location();
        if (location == null || location.isBlank()) {
            throw redirectRefused(response, "GitHub 리다이렉트 응답에 Location이 없습니다: " + response.path(), null);
        }
        URI target;
        try {
            target = response.uri().resolve(location.strip());
        } catch (IllegalArgumentException e) {
            throw redirectRefused(response, "GitHub 리다이렉트 위치를 해석하지 못했습니다: " + response.path(), e);
        }
        if (!isApiOrigin(target)) {
            throw redirectRefused(response, "GitHub API 밖으로 가는 리다이렉트는 따라가지 않습니다: " + target.getHost(), null);
        }
        return target;
    }

    private static Optional<String> nextPageLink(GitHubResponse response) {
        for (String link : response.headers().getOrEmpty(HttpHeaders.LINK)) {
            Matcher entry = LINK_ENTRY.matcher(link);
            while (entry.find()) {
                if (isNextPage(entry.group(2))) {
                    return Optional.of(entry.group(1));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isNextPage(String relations) {
        return Arrays.stream(relations.strip().split("\\s+")).anyMatch(NEXT_PAGE_RELATION::equalsIgnoreCase);
    }

    private Optional<URI> nextPageUri(GitHubResponse response) {
        return nextPageLink(response).map(link -> nextPageTarget(response, link));
    }

    private URI nextPageTarget(GitHubResponse response, String link) {
        URI target;
        try {
            target = response.uri().resolve(link.strip());
        } catch (IllegalArgumentException e) {
            throw invalidResponse("GitHub 다음 페이지 주소를 해석하지 못했습니다: " + response.path(), e);
        }
        if (!isApiOrigin(target)) {
            throw invalidResponse("GitHub API 밖을 가리키는 다음 페이지는 따라가지 않습니다: " + target.getHost(), null);
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

    private static void logRateLimit(URI firstPageUri, RateLimit rateLimit) {
        if (rateLimit == null) {
            return;
        }
        String resetAt = rateLimit.resetAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String retryAfter = rateLimit.retryAfter() == null ? "" : ", Retry-After " + rateLimit.retryAfter();
        if (rateLimit.isLow()) {
            log.warn("GitHub 한도가 적게 남았습니다 {}/{}, {} 초기화: {} 상태 {}{}",
                    rateLimit.remaining(), rateLimit.limit(), resetAt,
                    firstPageUri.getPath(), rateLimit.status(), retryAfter);
            return;
        }
        log.info("GitHub 한도 {}/{} 남음, {} 초기화: {} 상태 {}{}",
                rateLimit.remaining(), rateLimit.limit(), resetAt,
                firstPageUri.getPath(), rateLimit.status(), retryAfter);
    }

    private Optional<GitHubRepo> readRepo(GitHubResponse response) {
        HttpStatusCode status = response.status();
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return Optional.empty();
        }
        if (!status.isSameCodeAs(HttpStatus.OK)) {
            throw requestFailed(response);
        }
        return Optional.of(parseRepo(response));
    }

    private static Reason reasonFor(GitHubResponse response) {
        HttpStatusCode status = response.status();
        return switch (status.value()) {
            case 401 -> Reason.UNAUTHORIZED;
            case 403 -> response.showsRateLimit() ? Reason.RATE_LIMITED : Reason.REJECTED;
            case 429 -> Reason.RATE_LIMITED;
            default -> status.is4xxClientError() ? Reason.REJECTED : Reason.INVALID_RESPONSE;
        };
    }

    private GitHubRepo parseRepo(GitHubResponse response) {
        RepoPayload payload;
        try {
            payload = OBJECT_MAPPER.readValue(response.body(), RepoPayload.class);
        } catch (IOException e) {
            throw invalidResponse("GitHub 레포 응답을 읽지 못했습니다: " + response.path(), e);
        }
        if (payload == null || payload.id() == null || payload.fullName() == null) {
            throw invalidResponse("GitHub 레포 응답에 id나 full_name이 없습니다: " + response.path(), null);
        }
        return new GitHubRepo(payload.id(), payload.fullName(), payload.description(), payload.language(),
                payload.stargazersCount(), payload.privateRepo(), payload.hasIssues(), payload.archived());
    }

    private static List<GitHubIssue> readIssues(GitHubResponse response) {
        if (!response.status().isSameCodeAs(HttpStatus.OK)) {
            throw requestFailed(response);
        }
        IssuePayload[] payloads;
        try {
            payloads = OBJECT_MAPPER.readValue(response.body(), IssuePayload[].class);
        } catch (IOException e) {
            throw invalidResponse("GitHub 이슈 목록 응답을 읽지 못했습니다: " + response.path(), e);
        }
        if (payloads == null) {
            throw invalidResponse("GitHub 이슈 목록 응답이 배열이 아닙니다: " + response.path(), null);
        }
        return Arrays.stream(payloads)
                .map(payload -> toIssue(response, payload))
                .toList();
    }

    private static GitHubIssue toIssue(GitHubResponse response, IssuePayload payload) {
        if (payload == null || payload.id() == null || payload.number() == null || payload.title() == null) {
            throw invalidResponse("GitHub 이슈 응답에 id, 번호, 제목 중 빠진 것이 있습니다: " + response.path(), null);
        }
        UserPayload author = payload.user();
        return new GitHubIssue(
                payload.id(),
                payload.number(),
                payload.title(),
                payload.body(),
                payload.isPullRequest(),
                payload.assigneeCount(),
                author == null ? null : author.login(),
                author == null ? null : author.type(),
                toKst(payload.createdAt(), response),
                toKst(payload.updatedAt(), response));
    }

    private static GitHubIssueLookup readIssue(GitHubResponse response) {
        HttpStatusCode status = response.status();
        if (response.isRedirect()) {
            return GitHubIssueLookup.moved();
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND) || status.isSameCodeAs(HttpStatus.GONE)) {
            return GitHubIssueLookup.gone();
        }
        if (!status.isSameCodeAs(HttpStatus.OK)) {
            throw requestFailed(response);
        }
        return GitHubIssueLookup.found(parseIssueDetail(response));
    }

    private static GitHubIssueDetail parseIssueDetail(GitHubResponse response) {
        IssueDetailPayload payload;
        try {
            payload = OBJECT_MAPPER.readValue(response.body(), IssueDetailPayload.class);
        } catch (IOException e) {
            throw invalidResponse("GitHub 이슈 응답을 읽지 못했습니다: " + response.path(), e);
        }
        if (payload == null || payload.id() == null || payload.title() == null || payload.state() == null) {
            throw invalidResponse("GitHub 이슈 응답에 id, 제목, 상태 중 빠진 것이 있습니다: " + response.path(), null);
        }
        UserPayload author = payload.user();
        return new GitHubIssueDetail(
                payload.id(),
                payload.title(),
                payload.body(),
                labelNamesOf(payload.labels()),
                OPEN_STATE.equalsIgnoreCase(payload.state()),
                payload.isPullRequest(),
                payload.assigneeCount(),
                author == null ? null : author.login(),
                author == null ? null : author.type());
    }

    private static List<String> labelNamesOf(JsonNode labels) {
        if (labels == null || !labels.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode label : labels) {
            JsonNode name = label.isObject() ? label.get(LABEL_NAME_FIELD) : label;
            if (name != null && name.isTextual() && !name.asText().isBlank()) {
                names.add(name.asText());
            }
        }
        return names;
    }

    private static void keepLatest(Map<Long, GitHubIssue> issues, GitHubIssue candidate) {
        GitHubIssue kept = issues.get(candidate.githubId());
        if (kept != null && candidate.updatedAt().isBefore(kept.updatedAt())) {
            return;
        }
        issues.remove(candidate.githubId());
        issues.put(candidate.githubId(), candidate);
    }

    private static LocalDateTime toKst(String githubTime, GitHubResponse response) {
        if (githubTime == null) {
            throw invalidResponse("GitHub 이슈 응답에 생성이나 수정 시각이 없습니다: " + response.path(), null);
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(githubTime), KST);
        } catch (DateTimeException e) {
            throw invalidResponse("GitHub 이슈 시각을 읽지 못했습니다: " + response.path(), e);
        }
    }

    private static String toGitHubTime(LocalDateTime kst) {
        return DateTimeFormatter.ISO_INSTANT.format(kst.atZone(KST).toInstant().truncatedTo(ChronoUnit.SECONDS));
    }

    private static GitHubClientException requestFailed(GitHubResponse response) {
        HttpStatusCode status = response.status();
        return new GitHubClientException(reasonFor(response), status.value(),
                "GitHub 요청 실패(상태 " + status.value() + "): " + response.path(), null);
    }

    private static GitHubClientException redirectRefused(GitHubResponse response, String message, Throwable cause) {
        return new GitHubClientException(Reason.REDIRECT_REFUSED, response.status().value(), message, cause);
    }

    private static GitHubClientException invalidResponse(String message, Throwable cause) {
        return new GitHubClientException(Reason.INVALID_RESPONSE, HttpStatus.OK.value(), message, cause);
    }

    private record GitHubResponse(URI uri, HttpStatusCode status, HttpHeaders headers, byte[] body) {

        boolean isRedirect() {
            return REDIRECT_STATUSES.contains(status.value());
        }

        boolean isNotModified() {
            return status.isSameCodeAs(HttpStatus.NOT_MODIFIED);
        }

        String location() {
            return headers.getFirst(HttpHeaders.LOCATION);
        }

        String etag() {
            return headers.getETag();
        }

        String retryAfter() {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (!THROTTLED_STATUSES.contains(status.value()) || retryAfter == null || retryAfter.isBlank()) {
                return null;
            }
            return retryAfter.strip();
        }

        boolean showsRateLimit() {
            return retryAfter() != null || rateLimitExhausted();
        }

        private boolean rateLimitExhausted() {
            String remaining = headers.getFirst(RATE_LIMIT_REMAINING_HEADER);
            return remaining != null && remaining.strip().equals("0");
        }

        String path() {
            return uri.getPath();
        }
    }

    private record RateLimit(long limit, long remaining, OffsetDateTime resetAt, int status, String retryAfter) {

        static Optional<RateLimit> of(GitHubResponse response) {
            String limit = response.headers().getFirst(RATE_LIMIT_LIMIT_HEADER);
            String remaining = response.headers().getFirst(RATE_LIMIT_REMAINING_HEADER);
            String reset = response.headers().getFirst(RATE_LIMIT_RESET_HEADER);
            if (limit == null || remaining == null || reset == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(new RateLimit(
                        Long.parseLong(limit.strip()),
                        Long.parseLong(remaining.strip()),
                        OffsetDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(reset.strip())), KST),
                        response.status().value(),
                        response.retryAfter()));
            } catch (NumberFormatException | DateTimeException e) {
                return Optional.empty();
            }
        }

        boolean isLow() {
            return remaining * 100 <= limit * LOW_RATE_LIMIT_PERCENT;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IssuePayload(
            Long id,
            Integer number,
            String title,
            String body,
            UserPayload user,
            List<JsonNode> assignees,
            @JsonProperty("pull_request") JsonNode pullRequest,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("updated_at") String updatedAt) {

        boolean isPullRequest() {
            return pullRequest != null && !pullRequest.isNull();
        }

        int assigneeCount() {
            return assignees == null ? 0 : assignees.size();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IssueDetailPayload(
            Long id,
            String title,
            String body,
            String state,
            UserPayload user,
            JsonNode labels,
            List<JsonNode> assignees,
            @JsonProperty("pull_request") JsonNode pullRequest) {

        boolean isPullRequest() {
            return pullRequest != null && !pullRequest.isNull();
        }

        int assigneeCount() {
            return assignees == null ? 0 : assignees.size();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserPayload(String login, String type) {
    }
}
