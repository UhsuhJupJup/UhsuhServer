package uhsuhjupjup.backend.oss.pipeline.grading.application;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentialsMissingException;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueDetail;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueLookup;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueBodyHash;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult.StopReason;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueUngradableReason;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OssIssueGradingServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant STARTED_AT = LocalDateTime.of(2026, 10, 8, 9, 0).atZone(KST).toInstant();
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 7, 12, 0);
    private static final Long REPO_ID = 7L;
    private static final long REPO_GITHUB_ID = 1_296_269L;
    private static final int MAX_ISSUES = 20;
    private static final Duration MAX_DURATION = Duration.ofMinutes(10);
    private static final int MAX_FAILURES = 3;
    private static final Limit CANDIDATE_LIMIT = Limit.of(MAX_ISSUES + 1);
    private static final String STORED_BODY = "Steps to reproduce";
    private static final String STORED_BODY_HASH = OssIssueBodyHash.of(STORED_BODY);
    private static final String FETCHED_TITLE = "TITLEMARKER Retry interval is ignored";
    private static final String FETCHED_BODY = "BODYMARKER Steps to reproduce, edited";
    private static final String FETCHED_BODY_HASH = OssIssueBodyHash.of(FETCHED_BODY);
    private static final List<String> LABELS = List.of("bug", "good first issue");
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final OssIssueVerdict VERDICT = new OssIssueVerdict(OssIssueDifficulty.MEDIUM,
            OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.ABSENT, OssIssueEvidence.PARTIAL,
            false, null, "REASONKOMARKER", "REASONENMARKER", "SUMMARYKOMARKER", "SUMMARYENMARKER");
    private static final IssueGradingResult GRADED = new IssueGradingResult(VERDICT, MODEL);
    private static final String REQUEST_ID_LOG_KEY = "requestId";
    private static final String CALLER_REQUEST_ID = "req-7f3a";
    private static final GitHubClientException FIRST_OUTAGE = new GitHubClientException(
            GitHubClientException.Reason.UNAVAILABLE,
            "GitHub가 응답하지 않습니다(시도 3번, 마지막 ConnectException): /repositories/1296269/issues/1", null);

    @Mock
    private OssRepoRepository ossRepoRepository;

    @Mock
    private OssIssueRepository ossIssueRepository;

    @Mock
    private OssIssueGradeRepository ossIssueGradeRepository;

    @Mock
    private OssIssueGradeSaver ossIssueGradeSaver;

    @Mock
    private GitHubClient gitHubClient;

    @Mock
    private IssueGrader issueGrader;

    @Mock
    private LockProvider lockProvider;

    @Mock
    private SimpleLock gradingLock;

    private final OssRepo repo = OssRepo.create(REPO_GITHUB_ID, "octocat/Hello-World", null, "Java", 80);
    private final SteppingClock clock = new SteppingClock(STARTED_AT);

    private OssIssueGradingService ossIssueGradingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(repo, "id", REPO_ID);
        lenient().when(lockProvider.lock(any())).thenReturn(Optional.of(gradingLock));
        ossIssueGradingService = new OssIssueGradingService(ossRepoRepository, ossIssueRepository,
                ossIssueGradeRepository, ossIssueGradeSaver, gitHubClient, issueGrader, lockProvider, clock,
                MAX_ISSUES, MAX_DURATION, MAX_FAILURES);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @AfterEach
    void clearLogContext() {
        MDC.clear();
    }

    @Test
    void gradeRepo_missingOrSuspendedRepo_throwsNotFoundWithoutLockOrGitHub() {
        given(ossRepoRepository.findByIdAndStatus(REPO_ID, OssRepoStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ossIssueGradingService.gradeRepo(REPO_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OSS_REPO_NOT_FOUND));

        then(lockProvider).shouldHaveNoInteractions();
        then(ossIssueRepository).shouldHaveNoInteractions();
        then(gitHubClient).shouldHaveNoInteractions();
        then(issueGrader).shouldHaveNoInteractions();
    }

    @Test
    void gradeRepo_anotherRunHoldsRepoLock_throwsInProgressWithoutSelectingOrCallingGitHub() {
        givenActiveRepo();
        given(lockProvider.lock(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> ossIssueGradingService.gradeRepo(REPO_ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OSS_ISSUE_GRADING_IN_PROGRESS);
                    assertThat(e.getErrorCode().getStatus().value()).isEqualTo(409);
                });

        then(ossIssueRepository).shouldHaveNoInteractions();
        then(gitHubClient).shouldHaveNoInteractions();
        then(issueGrader).shouldHaveNoInteractions();
        then(gradingLock).shouldHaveNoInteractions();
    }

    @Test
    void gradeRepo_holdsRepoLockForRunLimitPlusMarginFromSelectingUntilSavedThenReleasesIt() {
        OssIssue issue = issue(1);
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);

        ossIssueGradingService.gradeRepo(REPO_ID);

        ArgumentCaptor<LockConfiguration> lockConfiguration = ArgumentCaptor.forClass(LockConfiguration.class);
        InOrder inOrder = inOrder(lockProvider, ossIssueRepository, gitHubClient, issueGrader, ossIssueGradeSaver,
                gradingLock);
        inOrder.verify(lockProvider).lock(lockConfiguration.capture());
        inOrder.verify(ossIssueRepository).findGradingCandidates(REPO_ID, MAX_FAILURES, CANDIDATE_LIMIT);
        inOrder.verify(gitHubClient).findIssue(REPO_GITHUB_ID, issue.getNumber());
        inOrder.verify(issueGrader).grade(FETCHED_TITLE, FETCHED_BODY, LABELS);
        inOrder.verify(ossIssueGradeSaver).save(issue, VERDICT, MODEL, FETCHED_BODY_HASH);
        inOrder.verify(gradingLock).unlock();
        assertThat(lockConfiguration.getValue().getName()).isEqualTo("ossIssueGrade-7");
        assertThat(lockConfiguration.getValue().getLockAtMostFor()).isEqualTo(Duration.ofMinutes(15));
        assertThat(lockConfiguration.getValue().getLockAtLeastFor()).isZero();
    }

    @Test
    void gradeRepo_noCandidates_completesWithEveryCountZeroAndEveryKeyPresent(CapturedOutput output) {
        givenActiveRepo();
        givenCandidates();

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()));
        assertThat(result.ungradable()).containsOnlyKeys(OssIssueUngradableReason.values())
                .allSatisfy((reason, count) -> assertThat(count).isZero());
        assertThat(result.failed())
                .containsOnlyKeys(Reason.REFUSED, Reason.TRUNCATED, Reason.INVALID_OUTPUT, Reason.INVALID_INPUT)
                .allSatisfy((reason, count) -> assertThat(count).isZero());
        assertThat(runLine(output)).contains("INFO").contains("멈춘 이유 COMPLETED");
        then(gitHubClient).shouldHaveNoInteractions();
        then(gradingLock).should().unlock();
    }

    @Test
    void gradeRepo_gradesFetchedTitleBodyAndLabelsAndSavesTheModelAndHashOfFetchedBodyWithoutTouchingTheIssue() {
        OssIssue issue = issue(1);
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 1, 1, 0, 0, 0, 0, 0, Map.of(), Map.of()));
        then(ossIssueGradeSaver).should().save(issue, VERDICT, MODEL, FETCHED_BODY_HASH);
        then(ossIssueGradeSaver).shouldHaveNoMoreInteractions();
        then(ossIssueRepository).should().findGradingCandidates(REPO_ID, MAX_FAILURES, CANDIDATE_LIMIT);
        then(ossIssueRepository).shouldHaveNoMoreInteractions();
        assertThat(issue.getBodyHash()).isEqualTo(STORED_BODY_HASH);
    }

    @Test
    void gradeRepo_gradeOfTheFetchedBodyAlreadyExists_skipsWithoutCallingTheGrader() {
        OssIssue issue = issue(1);
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(ossIssueGradeRepository.existsByIssueIdAndSourceHash(issue.getId(), FETCHED_BODY_HASH)).willReturn(true);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 1, 0, 0, 0, 1, 0, 0, Map.of(), Map.of()));
        then(issueGrader).shouldHaveNoInteractions();
        then(ossIssueGradeSaver).shouldHaveNoInteractions();
    }

    static Stream<Ungradable> ungradableIssues() {
        return Stream.of(
                new Ungradable(OssIssueUngradableReason.GONE, issue -> GitHubIssueLookup.gone()),
                new Ungradable(OssIssueUngradableReason.TRANSFERRED, issue -> GitHubIssueLookup.moved()),
                new Ungradable(OssIssueUngradableReason.ID_MISMATCH,
                        issue -> found(issue.getGithubIssueId() + 1, true, false, 0, "octocat", "User")),
                new Ungradable(OssIssueUngradableReason.CLOSED,
                        issue -> found(issue.getGithubIssueId(), false, false, 0, "octocat", "User")),
                new Ungradable(OssIssueUngradableReason.PULL_REQUEST,
                        issue -> found(issue.getGithubIssueId(), true, true, 0, "octocat", "User")),
                new Ungradable(OssIssueUngradableReason.BOT_AUTHOR,
                        issue -> found(issue.getGithubIssueId(), true, false, 0, "renovate", "Bot")),
                new Ungradable(OssIssueUngradableReason.BOT_AUTHOR,
                        issue -> found(issue.getGithubIssueId(), true, false, 0, "github-actions[bot]", null)),
                new Ungradable(OssIssueUngradableReason.ASSIGNED,
                        issue -> found(issue.getGithubIssueId(), true, false, 1, "octocat", "User")));
    }

    @ParameterizedTest
    @MethodSource("ungradableIssues")
    void gradeRepo_issueThatCannotBeGraded_countsAFailureOnTheStoredBodyHashWithoutCallingTheGrader(
            Ungradable ungradable) {
        OssIssue issue = issue(1);
        givenActiveRepo();
        givenCandidates(issue);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, issue.getNumber())).willReturn(ungradable.lookup().apply(issue));

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 1, 0, 0, 0, 0, 0, 0,
                Map.of(ungradable.reason(), 1), Map.of()));
        then(ossIssueGradeSaver).should().recordFailure(issue.getId(), STORED_BODY_HASH);
        then(ossIssueGradeSaver).shouldHaveNoMoreInteractions();
        then(issueGrader).shouldHaveNoInteractions();
        then(ossIssueGradeRepository).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 451, 422})
    void gradeRepo_githubRejectsThatIssue_countsItAsBlockedOnTheStoredBodyHashAndGoesOn(int status) {
        OssIssue blocked = issue(1);
        OssIssue next = issue(2);
        givenActiveRepo();
        givenCandidates(blocked, next);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, blocked.getNumber())).willThrow(new GitHubClientException(
                GitHubClientException.Reason.REJECTED, status, "GitHub 요청 실패(상태 " + status + ")", null));
        givenFound(next);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 2, 1, 0, 0, 0, 0, 0,
                Map.of(OssIssueUngradableReason.BLOCKED, 1), Map.of()));
        then(ossIssueGradeSaver).should().recordFailure(blocked.getId(), STORED_BODY_HASH);
        then(ossIssueGradeSaver).should().save(next, VERDICT, MODEL, FETCHED_BODY_HASH);
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"REFUSED", "TRUNCATED", "INVALID_OUTPUT", "INVALID_INPUT"})
    void gradeRepo_failureOfThatIssue_countsItOnTheFetchedBodyHashAndGoesOnToTheNextIssue(Reason reason) {
        OssIssue failing = issue(1);
        OssIssue next = issue(2);
        givenActiveRepo();
        givenCandidates(failing, next);
        givenFound(failing);
        givenFound(next);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS))
                .willThrow(new IssueGradingException(reason, "이 이슈만 실패"))
                .willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 2, 1, 0, 0, 0, 0, 0, Map.of(), Map.of(reason, 1)));
        then(ossIssueGradeSaver).should().recordFailure(failing.getId(), FETCHED_BODY_HASH);
        then(ossIssueGradeSaver).should().save(next, VERDICT, MODEL, FETCHED_BODY_HASH);
        then(ossIssueGradeSaver).shouldHaveNoMoreInteractions();
    }

    @Test
    void gradeRepo_fetchedBodyAlreadyFailedMaxTimes_skipsWithoutCallingTheGrader() {
        OssIssue issue = withGradingFailures(issue(1), MAX_FAILURES, FETCHED_BODY_HASH);
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 1, 0, 0, 0, 0, 1, 0, Map.of(), Map.of()));
        then(issueGrader).shouldHaveNoInteractions();
        then(ossIssueGradeSaver).shouldHaveNoInteractions();
    }

    @Test
    void gradeRepo_fetchedBodyFailedFewerThanMaxTimes_isGradedAgain() {
        OssIssue issue = withGradingFailures(issue(1), MAX_FAILURES - 1, FETCHED_BODY_HASH);
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);

        assertThat(ossIssueGradingService.gradeRepo(REPO_ID).graded()).isEqualTo(1);
    }

    @Test
    void gradeRepo_bodyChangedSinceItFailedMaxTimes_gradesTheNewBody() {
        OssIssue issue = withGradingFailures(issue(1), MAX_FAILURES, STORED_BODY_HASH);
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result.graded()).isEqualTo(1);
        then(ossIssueGradeSaver).should().save(issue, VERDICT, MODEL, FETCHED_BODY_HASH);
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"UNAVAILABLE", "REJECTED"})
    void gradeRepo_failureOfEveryCall_stopsHoldingThatIssueWithoutCountingItOrTouchingLaterIssues(Reason reason,
                                                                                               CapturedOutput output) {
        OssIssue held = issue(1);
        OssIssue later = issue(2);
        givenActiveRepo();
        givenCandidates(held, later);
        givenFound(held);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS))
                .willThrow(new IssueGradingException(reason, "이슈를 판정하지 못했습니다: Claude, GPT 실패"));

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        StopReason expected = reason == Reason.UNAVAILABLE ? StopReason.GRADER_UNAVAILABLE : StopReason.GRADER_REJECTED;
        assertThat(result).isEqualTo(result(expected, 2, 0, 1, 1, 0, 0, 0, Map.of(), Map.of()));
        assertThat(runLine(output)).contains("WARN").contains("멈춘 이유 " + expected)
                .contains("원인 이슈를 판정하지 못했습니다: Claude, GPT 실패");
        then(ossIssueGradeSaver).shouldHaveNoInteractions();
        then(gitHubClient).should(never()).findIssue(REPO_GITHUB_ID, later.getNumber());
        then(gradingLock).should().unlock();
    }

    static Stream<Arguments> githubFailuresThatStopTheRunAtOnce() {
        return Stream.of(
                Arguments.of(new GitHubCredentialsMissingException(), StopReason.GITHUB_NOT_CONFIGURED, "-"),
                Arguments.of(githubFailure(GitHubClientException.Reason.UNAUTHORIZED, 401),
                        StopReason.GITHUB_UNAUTHORIZED, "401"),
                Arguments.of(githubFailure(GitHubClientException.Reason.RATE_LIMITED, 403),
                        StopReason.GITHUB_RATE_LIMITED, "403"),
                Arguments.of(githubFailure(GitHubClientException.Reason.RATE_LIMITED, 429),
                        StopReason.GITHUB_RATE_LIMITED, "429"));
    }

    @ParameterizedTest
    @MethodSource("githubFailuresThatStopTheRunAtOnce")
    void gradeRepo_githubTokenAuthOrLimitFailure_stopsAtOnceHoldingTheIssueWithoutCountingOrTouchingLaterIssues(
            GitHubClientException failure, StopReason expected, String status, CapturedOutput output) {
        OssIssue held = issue(1);
        OssIssue later = issue(2);
        givenActiveRepo();
        givenCandidates(held, later);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, held.getNumber())).willThrow(failure);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(expected, 2, 0, 1, 1, 0, 0, 0, Map.of(), Map.of()));
        assertThat(issueLines(output)).singleElement().asString()
                .contains("issueId=1 number=1 outcome=HELD reason=" + expected + " status=" + status + " ");
        assertThat(runLine(output)).contains("WARN").contains("멈춘 이유 " + expected)
                .contains("원인 " + failure.getMessage());
        then(gitHubClient).should(never()).findIssue(REPO_GITHUB_ID, later.getNumber());
        then(ossIssueGradeSaver).shouldHaveNoInteractions();
        then(issueGrader).shouldHaveNoInteractions();
    }

    static Stream<Arguments> githubOutages() {
        return Stream.of(
                Arguments.of(new GitHubClientException(GitHubClientException.Reason.UNAVAILABLE,
                        "GitHub가 응답하지 않습니다(시도 3번, 마지막 상태 502): /repositories/1296269/issues/2", null), "-"),
                Arguments.of(new GitHubClientException(GitHubClientException.Reason.INVALID_RESPONSE, 200,
                        "GitHub 이슈 응답을 읽지 못했습니다: /repositories/1296269/issues/2", null), "200"),
                Arguments.of(new GitHubClientException(GitHubClientException.Reason.REDIRECT_REFUSED, 301,
                        "GitHub 리다이렉트가 3번을 넘었습니다: /repositories/1296269/issues/2", null), "301"));
    }

    @ParameterizedTest
    @MethodSource("githubOutages")
    void gradeRepo_oneGitHubOutage_skipsThatIssueWithoutCountingAFailureAndGoesOnToTheEnd(
            GitHubClientException outage, String status, CapturedOutput output) {
        OssIssue skipped = issue(1);
        OssIssue next = issue(2);
        givenActiveRepo();
        givenCandidates(skipped, next);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, skipped.getNumber())).willThrow(outage);
        givenFound(next);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 2, 1, 0, 0, 0, 0, 1, Map.of(), Map.of()));
        assertThat(issueLines(output).get(0)).contains("issueId=1 number=1 outcome=GITHUB_SKIPPED reason="
                + outage.getReason() + " status=" + status + " ");
        assertThat(runLine(output)).contains("INFO").contains("멈춘 이유 COMPLETED").contains("GitHub 장애로 건너뜀 1");
        then(ossIssueGradeSaver).should(never()).recordFailure(anyLong(), anyString());
        then(ossIssueGradeSaver).should().save(next, VERDICT, MODEL, FETCHED_BODY_HASH);
    }

    @ParameterizedTest
    @MethodSource("githubOutages")
    void gradeRepo_twoGitHubOutagesInARow_stopsHoldingTheSecondWithTheLastOutageAsCause(
            GitHubClientException lastOutage, String status, CapturedOutput output) {
        OssIssue skipped = issue(1);
        OssIssue held = issue(2);
        OssIssue later = issue(3);
        givenActiveRepo();
        givenCandidates(skipped, held, later);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, skipped.getNumber())).willThrow(FIRST_OUTAGE);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, held.getNumber())).willThrow(lastOutage);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.GITHUB_UNAVAILABLE, 3, 0, 1, 1, 0, 0, 1, Map.of(), Map.of()));
        assertThat(issueLines(output)).satisfiesExactly(
                line -> assertThat(line).contains("issueId=1 number=1 outcome=GITHUB_SKIPPED reason=UNAVAILABLE"),
                line -> assertThat(line).contains("issueId=2 number=2 outcome=HELD reason=GITHUB_UNAVAILABLE status="
                        + status + " "));
        assertThat(runLine(output)).contains("WARN").contains("멈춘 이유 GITHUB_UNAVAILABLE")
                .contains("원인 " + lastOutage.getMessage())
                .doesNotContain(FIRST_OUTAGE.getMessage());
        then(gitHubClient).should(never()).findIssue(REPO_GITHUB_ID, later.getNumber());
        then(ossIssueGradeSaver).shouldHaveNoInteractions();
        then(issueGrader).shouldHaveNoInteractions();
    }

    enum AnswerBetweenOutages {
        FOUND,
        GONE,
        MOVED,
        BLOCKED
    }

    @ParameterizedTest
    @EnumSource(AnswerBetweenOutages.class)
    void gradeRepo_outageThenAnAnswerThenOutage_skipsBothOutagesWithoutStopping(AnswerBetweenOutages answer) {
        OssIssue first = issue(1);
        OssIssue middle = issue(2);
        OssIssue last = issue(3);
        givenActiveRepo();
        givenCandidates(first, middle, last);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, first.getNumber())).willThrow(FIRST_OUTAGE);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, last.getNumber())).willThrow(FIRST_OUTAGE);
        switch (answer) {
            case FOUND -> {
                givenFound(middle);
                given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);
            }
            case GONE -> given(gitHubClient.findIssue(REPO_GITHUB_ID, middle.getNumber()))
                    .willReturn(GitHubIssueLookup.gone());
            case MOVED -> given(gitHubClient.findIssue(REPO_GITHUB_ID, middle.getNumber()))
                    .willReturn(GitHubIssueLookup.moved());
            case BLOCKED -> given(gitHubClient.findIssue(REPO_GITHUB_ID, middle.getNumber()))
                    .willThrow(githubFailure(GitHubClientException.Reason.REJECTED, 451));
        }

        OssIssueGradingRunResult result = assertCountsAddUp(ossIssueGradingService.gradeRepo(REPO_ID));

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.githubSkipped()).isEqualTo(2);
        assertThat(result.held()).isZero();
        then(ossIssueGradeSaver).should(never()).recordFailure(eq(first.getId()), anyString());
        then(ossIssueGradeSaver).should(never()).recordFailure(eq(last.getId()), anyString());
    }

    @Test
    void gradeRepo_outageOnlyOnTheLastIssue_completesWithThatIssueSkipped() {
        OssIssue graded = issue(1);
        OssIssue skipped = issue(2);
        givenActiveRepo();
        givenCandidates(graded, skipped);
        givenFound(graded);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, skipped.getNumber())).willThrow(FIRST_OUTAGE);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 2, 1, 0, 0, 0, 0, 1, Map.of(), Map.of()));
        then(ossIssueGradeSaver).should(never()).recordFailure(eq(skipped.getId()), anyString());
    }

    @Test
    void gradeRepo_moreCandidatesThanTheLimit_gradesTheFirstTwentyInOrderAndStopsAtTheIssueLimit() {
        List<OssIssue> candidates = IntStream.rangeClosed(1, MAX_ISSUES + 1).mapToObj(this::issue).toList();
        givenActiveRepo();
        given(ossIssueRepository.findGradingCandidates(REPO_ID, MAX_FAILURES, CANDIDATE_LIMIT)).willReturn(candidates);
        given(gitHubClient.findIssue(eq(REPO_GITHUB_ID), anyInt())).willAnswer(invocation ->
                GitHubIssueLookup.found(detailOf(candidates.get(invocation.<Integer>getArgument(1) - 1))));
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.ISSUE_LIMIT, MAX_ISSUES, MAX_ISSUES, 0, 0, 0, 0, 0,
                Map.of(), Map.of()));
        InOrder inOrder = inOrder(gitHubClient);
        candidates.subList(0, MAX_ISSUES).forEach(issue ->
                inOrder.verify(gitHubClient).findIssue(REPO_GITHUB_ID, issue.getNumber()));
        then(gitHubClient).should(never()).findIssue(REPO_GITHUB_ID, MAX_ISSUES + 1);
    }

    @Test
    void gradeRepo_exactlyAsManyCandidatesAsTheLimit_completes() {
        List<OssIssue> candidates = IntStream.rangeClosed(1, MAX_ISSUES).mapToObj(this::issue).toList();
        givenActiveRepo();
        given(ossIssueRepository.findGradingCandidates(REPO_ID, MAX_FAILURES, CANDIDATE_LIMIT)).willReturn(candidates);
        given(gitHubClient.findIssue(eq(REPO_GITHUB_ID), anyInt())).willReturn(GitHubIssueLookup.gone());

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.ungradable()).containsEntry(OssIssueUngradableReason.GONE, MAX_ISSUES);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, MAX_ISSUES + 5})
    void gradeRepo_requestedCount_gradesUpToThatManyWhateverTheConfiguredDefault(int count) {
        List<OssIssue> candidates = IntStream.rangeClosed(1, count + 1).mapToObj(this::issue).toList();
        givenActiveRepo();
        given(ossIssueRepository.findGradingCandidates(REPO_ID, MAX_FAILURES, Limit.of(count + 1)))
                .willReturn(candidates);
        given(gitHubClient.findIssue(eq(REPO_GITHUB_ID), anyInt())).willAnswer(invocation ->
                GitHubIssueLookup.found(detailOf(candidates.get(invocation.<Integer>getArgument(1) - 1))));
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID, count);

        assertThat(result).isEqualTo(result(StopReason.ISSUE_LIMIT, count, count, 0, 0, 0, 0, 0, Map.of(), Map.of()));
        then(gitHubClient).should(never()).findIssue(REPO_GITHUB_ID, count + 1);
    }

    @Test
    void gradeRepo_requestedCountAboveTheCandidates_completes() {
        givenActiveRepo();
        given(ossIssueRepository.findGradingCandidates(REPO_ID, MAX_FAILURES, Limit.of(6)))
                .willReturn(List.of(issue(1), issue(2)));
        given(gitHubClient.findIssue(eq(REPO_GITHUB_ID), anyInt())).willReturn(GitHubIssueLookup.gone());

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID, 5);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 2, 0, 0, 0, 0, 0, 0,
                Map.of(OssIssueUngradableReason.GONE, 2), Map.of()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void gradeRepo_requestedCountBelowOne_isRefusedBeforeLookingUpTheRepoOrTakingTheLock(int count) {
        assertThatThrownBy(() -> ossIssueGradingService.gradeRepo(REPO_ID, count))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(count));

        then(ossRepoRepository).shouldHaveNoInteractions();
        then(lockProvider).shouldHaveNoInteractions();
        then(ossIssueRepository).shouldHaveNoInteractions();
    }

    @Test
    void gradeRepo_keepsEachIssueIdInTheLogContextOnlyWhileThatIssueIsBeingFetchedGradedAndSaved() {
        OssIssue first = issue(1);
        OssIssue second = issue(2);
        List<String> contexts = new ArrayList<>();
        givenActiveRepo();
        given(ossIssueRepository.findGradingCandidates(REPO_ID, MAX_FAILURES, CANDIDATE_LIMIT)).willAnswer(invocation -> {
            contexts.add("select " + issueIdInLogContext());
            return List.of(first, second);
        });
        given(gitHubClient.findIssue(eq(REPO_GITHUB_ID), anyInt())).willAnswer(invocation -> {
            contexts.add("fetch #" + invocation.getArgument(1) + " " + issueIdInLogContext());
            return GitHubIssueLookup.found(detailOf(invocation.<Integer>getArgument(1) == 1 ? first : second));
        });
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willAnswer(invocation -> {
            contexts.add("grade " + issueIdInLogContext());
            return GRADED;
        });
        willAnswer(invocation -> {
            contexts.add("save " + issueIdInLogContext());
            return null;
        }).given(ossIssueGradeSaver).save(any(), eq(VERDICT), eq(MODEL), eq(FETCHED_BODY_HASH));
        willAnswer(invocation -> {
            contexts.add("unlock " + issueIdInLogContext());
            return null;
        }).given(gradingLock).unlock();

        ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(contexts).containsExactly("select null", "fetch #1 1", "grade 1", "save 1",
                "fetch #2 2", "grade 2", "save 2", "unlock null");
        assertThat(issueIdInLogContext()).isNull();
    }

    @Test
    void gradeRepo_keepsWhatTheCallerPutInTheLogContextWhileGradingAndAfterwards() {
        OssIssue first = issue(1);
        OssIssue second = issue(2);
        List<String> contexts = new ArrayList<>();
        MDC.put(REQUEST_ID_LOG_KEY, CALLER_REQUEST_ID);
        givenActiveRepo();
        givenCandidates(first, second);
        given(gitHubClient.findIssue(eq(REPO_GITHUB_ID), anyInt())).willAnswer(invocation ->
                GitHubIssueLookup.found(detailOf(invocation.<Integer>getArgument(1) == 1 ? first : second)));
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willAnswer(invocation -> {
            contexts.add(MDC.get(REQUEST_ID_LOG_KEY) + " " + issueIdInLogContext());
            return GRADED;
        });

        ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(contexts).containsExactly(CALLER_REQUEST_ID + " 1", CALLER_REQUEST_ID + " 2");
        assertThat(MDC.get(REQUEST_ID_LOG_KEY)).isEqualTo(CALLER_REQUEST_ID);
        assertThat(issueIdInLogContext()).isNull();
    }

    @Test
    void gradeRepo_runStoppedOnAnIssue_leavesNoIssueIdInTheLogContext() {
        OssIssue held = issue(1);
        givenActiveRepo();
        givenCandidates(held, issue(2));
        givenFound(held);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS))
                .willThrow(new IssueGradingException(Reason.UNAVAILABLE, "이슈를 판정하지 못했습니다: Claude, GPT 실패"));

        assertThat(ossIssueGradingService.gradeRepo(REPO_ID).stopReason()).isEqualTo(StopReason.GRADER_UNAVAILABLE);

        assertThat(issueIdInLogContext()).isNull();
    }

    @Test
    void gradeRepo_failureThrownWhileHandlingAnIssue_removesOnlyTheIssueIdFromTheLogContext() {
        OssIssue issue = issue(1);
        DataAccessResourceFailureException saveFailure = new DataAccessResourceFailureException("connection lost");
        MDC.put(REQUEST_ID_LOG_KEY, CALLER_REQUEST_ID);
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);
        willThrow(saveFailure).given(ossIssueGradeSaver).save(any(), any(), any(), any());

        assertThatThrownBy(() -> ossIssueGradingService.gradeRepo(REPO_ID)).isSameAs(saveFailure);

        assertThat(issueIdInLogContext()).isNull();
        assertThat(MDC.get(REQUEST_ID_LOG_KEY)).isEqualTo(CALLER_REQUEST_ID);
    }

    @Test
    void gradeRepo_tenMinutesPassed_stopsBeforeStartingTheNextIssueLeavingItForTheNextRun() {
        OssIssue first = issue(1);
        OssIssue second = issue(2);
        OssIssue third = issue(3);
        givenActiveRepo();
        givenCandidates(first, second, third);
        givenFound(first);
        givenFound(second);
        willAnswer(invocation -> {
            clock.advance(Duration.ofMinutes(5));
            return GRADED;
        }).given(issueGrader).grade(FETCHED_TITLE, FETCHED_BODY, LABELS);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.TIME_LIMIT, 3, 2, 0, 1, 0, 0, 0, Map.of(), Map.of()));
        then(gitHubClient).should(never()).findIssue(REPO_GITHUB_ID, third.getNumber());
    }

    @Test
    void gradeRepo_tenMinutesPassedWhileFetching_holdsThatIssueWithoutCallingTheGrader(CapturedOutput output) {
        OssIssue first = issue(1);
        OssIssue second = issue(2);
        givenActiveRepo();
        givenCandidates(first, second);
        willAnswer(invocation -> {
            clock.advance(MAX_DURATION);
            return GitHubIssueLookup.found(detailOf(first));
        }).given(gitHubClient).findIssue(REPO_GITHUB_ID, first.getNumber());

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.TIME_LIMIT, 2, 0, 1, 1, 0, 0, 0, Map.of(), Map.of()));
        assertThat(issueLines(output)).singleElement().asString()
                .contains("issueId=1 number=1 outcome=HELD reason=TIME_LIMIT");
        assertThat(runLine(output)).contains("INFO").contains("멈춘 이유 TIME_LIMIT");
        then(issueGrader).shouldHaveNoInteractions();
        then(ossIssueGradeSaver).shouldHaveNoInteractions();
        then(gitHubClient).should(never()).findIssue(REPO_GITHUB_ID, second.getNumber());
    }

    @Test
    void gradeRepo_justUnderTenMinutesBeforeTheGraderOrTheNextIssue_keepsGoing() {
        OssIssue first = issue(1);
        OssIssue second = issue(2);
        givenActiveRepo();
        givenCandidates(first, second);
        willAnswer(invocation -> {
            clock.advance(MAX_DURATION.minusMillis(2));
            return GitHubIssueLookup.found(detailOf(first));
        }).given(gitHubClient).findIssue(REPO_GITHUB_ID, first.getNumber());
        givenFound(second);
        willAnswer(invocation -> {
            clock.advance(Duration.ofMillis(1));
            return GRADED;
        }).given(issueGrader).grade(FETCHED_TITLE, FETCHED_BODY, LABELS);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 2, 2, 0, 0, 0, 0, 0, Map.of(), Map.of()));
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void gradeRepo_interruptedWhileGrading_stopsHoldingTheIssueWithoutCountingAndKeepsTheInterrupt(Reason reason) {
        OssIssue interrupted = issue(1);
        OssIssue later = issue(2);
        givenActiveRepo();
        givenCandidates(interrupted, later);
        givenFound(interrupted);
        willAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new IssueGradingException(reason, "이슈 판정이 중단됐습니다");
        }).given(issueGrader).grade(FETCHED_TITLE, FETCHED_BODY, LABELS);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.INTERRUPTED, 2, 0, 1, 1, 0, 0, 0, Map.of(), Map.of()));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        then(ossIssueGradeSaver).shouldHaveNoInteractions();
        then(gitHubClient).should(never()).findIssue(REPO_GITHUB_ID, later.getNumber());
        then(gradingLock).should().unlock();
    }

    @ParameterizedTest
    @EnumSource(value = GitHubClientException.Reason.class, names = {"UNAVAILABLE", "REJECTED"})
    void gradeRepo_interruptedWhileFetching_stopsHoldingTheIssueWithoutCountingTheGitHubFailure(
            GitHubClientException.Reason reason) {
        OssIssue issue = issue(1);
        givenActiveRepo();
        givenCandidates(issue, issue(2));
        willAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new GitHubClientException(reason, "GitHub 재시도를 기다리다 중단됐습니다", null);
        }).given(gitHubClient).findIssue(REPO_GITHUB_ID, issue.getNumber());

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.INTERRUPTED, 2, 0, 1, 1, 0, 0, 0, Map.of(), Map.of()));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        then(ossIssueGradeSaver).shouldHaveNoInteractions();
    }

    @Test
    void gradeRepo_interruptedBeforeTheFirstIssue_stopsWithoutHoldingAnyOrCallingGitHub(CapturedOutput output) {
        givenActiveRepo();
        givenCandidates(issue(1));
        Thread.currentThread().interrupt();

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.INTERRUPTED, 1, 0, 0, 1, 0, 0, 0, Map.of(), Map.of()));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(runLine(output)).contains("WARN").contains("멈춘 이유 INTERRUPTED");
        then(gitHubClient).shouldHaveNoInteractions();
        then(gradingLock).should().unlock();
    }

    @Test
    void gradeRepo_releasingTheLockFails_stillReturnsTheResult() {
        givenActiveRepo();
        givenCandidates();
        willThrow(new DataAccessResourceFailureException("Communications link failure")).given(gradingLock).unlock();

        assertThat(ossIssueGradingService.gradeRepo(REPO_ID).stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    static Stream<PessimisticLockingFailureException> clashesWithAnotherWriter() {
        return Stream.of(
                new CannotAcquireLockException("Deadlock found when trying to get lock"),
                new PessimisticLockingFailureException("Lock wait timeout exceeded"));
    }

    @ParameterizedTest
    @MethodSource("clashesWithAnotherWriter")
    void gradeRepo_savingClashesWithAnotherWriterOnce_savesOnceMoreWithoutGradingAgain(
            PessimisticLockingFailureException clash) {
        OssIssue issue = issue(1);
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);
        willThrow(clash).willAnswer(invocation -> null)
                .given(ossIssueGradeSaver).save(issue, VERDICT, MODEL, FETCHED_BODY_HASH);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result.graded()).isEqualTo(1);
        then(ossIssueGradeSaver).should(times(2)).save(issue, VERDICT, MODEL, FETCHED_BODY_HASH);
        then(issueGrader).should(times(1)).grade(FETCHED_TITLE, FETCHED_BODY, LABELS);
    }

    @Test
    void gradeRepo_savingClashesTwice_throwsTheSecondFailureWithTheFirstSuppressedAndReleasesTheLock() {
        OssIssue issue = issue(1);
        CannotAcquireLockException first = new CannotAcquireLockException("Deadlock found when trying to get lock");
        PessimisticLockingFailureException again = new PessimisticLockingFailureException("Lock wait timeout exceeded");
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);
        willThrow(first).willThrow(again).given(ossIssueGradeSaver).save(issue, VERDICT, MODEL, FETCHED_BODY_HASH);

        assertThatThrownBy(() -> ossIssueGradingService.gradeRepo(REPO_ID))
                .isSameAs(again)
                .hasSuppressedException(first);

        then(ossIssueGradeSaver).should(times(2)).save(issue, VERDICT, MODEL, FETCHED_BODY_HASH);
        then(gradingLock).should().unlock();
    }

    @Test
    void gradeRepo_issueDeletedBeforeItsGradeWasSaved_skipsOnlyThatIssue() {
        OssIssue deleted = issue(1);
        OssIssue next = issue(2);
        givenActiveRepo();
        givenCandidates(deleted, next);
        givenFound(deleted);
        givenFound(next);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);
        willThrow(new DataIntegrityViolationException("fk_oss_issue_grade_issue"))
                .given(ossIssueGradeSaver).save(deleted, VERDICT, MODEL, FETCHED_BODY_HASH);
        given(ossIssueRepository.existsById(deleted.getId())).willReturn(false);

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.COMPLETED, 2, 1, 0, 0, 0, 0, 0,
                Map.of(OssIssueUngradableReason.DELETED, 1), Map.of()));
        then(ossIssueGradeSaver).should(never()).recordFailure(anyLong(), anyString());
        then(ossIssueGradeSaver).should().save(next, VERDICT, MODEL, FETCHED_BODY_HASH);
    }

    @Test
    void gradeRepo_integrityFailureWhileTheIssueStillExists_isThrown() {
        OssIssue issue = issue(1);
        DataIntegrityViolationException tooLong = new DataIntegrityViolationException("Data too long for column");
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);
        willThrow(tooLong).given(ossIssueGradeSaver).save(issue, VERDICT, MODEL, FETCHED_BODY_HASH);
        given(ossIssueRepository.existsById(issue.getId())).willReturn(true);

        assertThatThrownBy(() -> ossIssueGradingService.gradeRepo(REPO_ID)).isSameAs(tooLong);

        then(gradingLock).should().unlock();
    }

    @Test
    void gradeRepo_otherSavingFailure_isThrownWithoutRetryAndReleasesTheLock() {
        OssIssue issue = issue(1);
        DataAccessResourceFailureException saveFailure = new DataAccessResourceFailureException("connection lost");
        givenActiveRepo();
        givenCandidates(issue);
        givenFound(issue);
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS)).willReturn(GRADED);
        willThrow(saveFailure).given(ossIssueGradeSaver).save(any(), any(), any(), any());

        assertThatThrownBy(() -> ossIssueGradingService.gradeRepo(REPO_ID)).isSameAs(saveFailure);

        then(ossIssueGradeSaver).should(times(1)).save(any(), any(), any(), any());
        then(gradingLock).should().unlock();
    }

    @Test
    void gradeRepo_logsOneLinePerStartedIssueWithoutTitleBodyOrGradeSentencesAndWarnsWhyItStopped(
            CapturedOutput output) {
        OssIssue graded = issue(1);
        OssIssue failed = issue(2);
        OssIssue gone = issue(3);
        OssIssue sameBody = issue(4);
        OssIssue blocked = issue(5);
        OssIssue skippedByOutage = issue(6);
        OssIssue held = issue(7);
        OssIssue notStarted = issue(8);
        GitHubClientException rateLimited = githubFailure(GitHubClientException.Reason.RATE_LIMITED, 403);
        givenActiveRepo();
        givenCandidates(graded, failed, gone, sameBody, blocked, skippedByOutage, held, notStarted);
        givenFound(graded);
        givenFound(failed);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, gone.getNumber())).willReturn(GitHubIssueLookup.gone());
        givenFound(sameBody);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, blocked.getNumber()))
                .willThrow(githubFailure(GitHubClientException.Reason.REJECTED, 451));
        given(gitHubClient.findIssue(REPO_GITHUB_ID, skippedByOutage.getNumber())).willThrow(FIRST_OUTAGE);
        given(gitHubClient.findIssue(REPO_GITHUB_ID, held.getNumber())).willThrow(rateLimited);
        given(ossIssueGradeRepository.existsByIssueIdAndSourceHash(anyLong(), eq(FETCHED_BODY_HASH)))
                .willAnswer(invocation -> sameBody.getId().equals(invocation.getArgument(0)));
        given(issueGrader.grade(FETCHED_TITLE, FETCHED_BODY, LABELS))
                .willReturn(GRADED)
                .willThrow(new IssueGradingException(Reason.INVALID_OUTPUT, "Claude 판정 출력이 규칙을 어겼습니다"));

        OssIssueGradingRunResult result = ossIssueGradingService.gradeRepo(REPO_ID);

        assertThat(result).isEqualTo(result(StopReason.GITHUB_RATE_LIMITED, 8, 1, 1, 1, 1, 0, 1,
                Map.of(OssIssueUngradableReason.GONE, 1, OssIssueUngradableReason.BLOCKED, 1),
                Map.of(Reason.INVALID_OUTPUT, 1)));
        assertThat(issueLines(output)).hasSize(7).satisfiesExactly(
                line -> assertThat(line).contains("issueId=1 number=1 outcome=GRADED reason=- status=- model="
                        + MODEL + " criteria=v1"),
                line -> assertThat(line).contains("issueId=2 number=2 outcome=FAILED reason=INVALID_OUTPUT status=- "
                        + "model=- criteria=v1"),
                line -> assertThat(line).contains("issueId=3 number=3 outcome=UNGRADABLE reason=GONE status=-"),
                line -> assertThat(line).contains("issueId=4 number=4 outcome=SAME_BODY reason=-"),
                line -> assertThat(line).contains("issueId=5 number=5 outcome=UNGRADABLE reason=BLOCKED status=451"),
                line -> assertThat(line).contains("issueId=6 number=6 outcome=GITHUB_SKIPPED reason=UNAVAILABLE "
                        + "status=-"),
                line -> assertThat(line).contains("issueId=7 number=7 outcome=HELD reason=GITHUB_RATE_LIMITED "
                        + "status=403"));
        assertThat(runLine(output)).contains("WARN")
                .contains("오픈소스 레포 octocat/Hello-World 이슈 판정: 멈춘 이유 GITHUB_RATE_LIMITED, 고름 8, 판정 1, "
                        + "보류 1, 남김 1, 같은 본문 1, 실패 상한 0, GitHub 장애로 건너뜀 1")
                .contains("원인 " + rateLimited.getMessage());
        assertThat(output.getAll())
                .doesNotContain("TITLEMARKER", "BODYMARKER", STORED_BODY, "REASONKOMARKER", "REASONENMARKER",
                        "SUMMARYKOMARKER", "SUMMARYENMARKER");
    }

    private void givenActiveRepo() {
        given(ossRepoRepository.findByIdAndStatus(REPO_ID, OssRepoStatus.ACTIVE)).willReturn(Optional.of(repo));
    }

    private void givenCandidates(OssIssue... issues) {
        given(ossIssueRepository.findGradingCandidates(REPO_ID, MAX_FAILURES, CANDIDATE_LIMIT))
                .willReturn(List.of(issues));
    }

    private void givenFound(OssIssue issue) {
        given(gitHubClient.findIssue(REPO_GITHUB_ID, issue.getNumber()))
                .willReturn(GitHubIssueLookup.found(detailOf(issue)));
    }

    private OssIssue issue(int number) {
        OssIssue issue = OssIssue.create(repo, 5_000L + number, number, "Stored title " + number, STORED_BODY,
                OPENED_AT.minusHours(number));
        ReflectionTestUtils.setField(issue, "id", (long) number);
        return issue;
    }

    private static OssIssue withGradingFailures(OssIssue issue, int failures, String sourceHash) {
        ReflectionTestUtils.setField(issue, "gradingFailures", failures);
        ReflectionTestUtils.setField(issue, "gradingFailureSourceHash", sourceHash);
        return issue;
    }

    private static GitHubIssueDetail detailOf(OssIssue issue) {
        return new GitHubIssueDetail(issue.getGithubIssueId(), FETCHED_TITLE, FETCHED_BODY, LABELS, true, false, 0,
                "octocat", "User");
    }

    private static GitHubIssueLookup found(long githubId, boolean open, boolean pullRequest, int assigneeCount,
                                           String authorLogin, String authorType) {
        return GitHubIssueLookup.found(new GitHubIssueDetail(githubId, FETCHED_TITLE, FETCHED_BODY, LABELS, open,
                pullRequest, assigneeCount, authorLogin, authorType));
    }

    private static GitHubClientException githubFailure(GitHubClientException.Reason reason, int status) {
        return new GitHubClientException(reason, status,
                "GitHub 요청 실패(상태 " + status + "): /repositories/1296269/issues/1", null);
    }

    private static OssIssueGradingRunResult result(StopReason stopReason, int selected, int graded, int held,
                                                   int notStarted, int sameBodySkipped, int failureLimitSkipped,
                                                   int githubSkipped,
                                                   Map<OssIssueUngradableReason, Integer> ungradable,
                                                   Map<Reason, Integer> failed) {
        return assertCountsAddUp(new OssIssueGradingRunResult(stopReason, selected, graded, held, notStarted,
                sameBodySkipped, failureLimitSkipped, githubSkipped, ungradable, failed));
    }

    private static OssIssueGradingRunResult assertCountsAddUp(OssIssueGradingRunResult result) {
        int accounted = result.graded() + result.held() + result.notStarted() + result.sameBodySkipped()
                + result.failureLimitSkipped() + result.githubSkipped()
                + result.ungradable().values().stream().mapToInt(Integer::intValue).sum()
                + result.failed().values().stream().mapToInt(Integer::intValue).sum();
        assertThat(accounted).as("every selected issue lands in exactly one count").isEqualTo(result.selected());
        return result;
    }

    private static String issueIdInLogContext() {
        return MDC.get(IssueGrader.ISSUE_ID_LOG_KEY);
    }

    private static List<String> issueLines(CapturedOutput output) {
        return output.getAll().lines().filter(line -> line.contains("오픈소스 이슈 판정 issueId=")).toList();
    }

    private static String runLine(CapturedOutput output) {
        return output.getAll().lines()
                .filter(line -> line.contains("이슈 판정: 멈춘 이유"))
                .reduce((earlier, later) -> later)
                .orElseThrow();
    }

    record Ungradable(OssIssueUngradableReason reason, Function<OssIssue, GitHubIssueLookup> lookup) {

        @Override
        public String toString() {
            return reason.name();
        }
    }

    private static final class SteppingClock extends Clock {

        private Instant now;

        SteppingClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
