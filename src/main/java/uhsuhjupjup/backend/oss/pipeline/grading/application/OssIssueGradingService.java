package uhsuhjupjup.backend.oss.pipeline.grading.application;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueDetail;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubIssueLookup;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueBodyHash;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult.StopReason;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueGradingCriteria;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueUngradableReason;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssueAuthorType;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

@Slf4j
@Service
public class OssIssueGradingService {

    private static final String GRADING_LOCK_NAME_PREFIX = "ossIssueGrade-";
    private static final Duration GRADING_LOCK_MARGIN = Duration.ofMinutes(5);
    private static final int GITHUB_OUTAGES_IN_A_ROW_TO_STOP = 2;
    private static final Set<StopReason> EXPECTED_STOPS =
            EnumSet.of(StopReason.COMPLETED, StopReason.ISSUE_LIMIT, StopReason.TIME_LIMIT);
    private static final String NONE = "-";
    private static final String INTERRUPTED_CAUSE = "스레드가 중단됐습니다";

    private enum Outcome {
        GRADED,
        SAME_BODY,
        FAILURE_LIMIT,
        UNGRADABLE,
        FAILED,
        GITHUB_SKIPPED,
        HELD
    }

    private final OssRepoRepository ossRepoRepository;
    private final OssIssueRepository ossIssueRepository;
    private final OssIssueGradeRepository ossIssueGradeRepository;
    private final OssIssueGradeSaver ossIssueGradeSaver;
    private final GitHubClient gitHubClient;
    private final IssueGrader issueGrader;
    private final LockProvider lockProvider;
    private final Clock clock;
    private final int maxIssues;
    private final Duration maxDuration;
    private final int maxFailures;
    private final Duration gradingLockAtMostFor;

    public OssIssueGradingService(OssRepoRepository ossRepoRepository,
                                  OssIssueRepository ossIssueRepository,
                                  OssIssueGradeRepository ossIssueGradeRepository,
                                  OssIssueGradeSaver ossIssueGradeSaver,
                                  GitHubClient gitHubClient,
                                  IssueGrader issueGrader,
                                  LockProvider lockProvider,
                                  Clock clock,
                                  @Value("${oss.grading.run.max-issues:20}") int maxIssues,
                                  @Value("${oss.grading.run.max-duration:PT10M}") Duration maxDuration,
                                  @Value("${oss.grading.run.max-failures:3}") int maxFailures) {
        this.ossRepoRepository = ossRepoRepository;
        this.ossIssueRepository = ossIssueRepository;
        this.ossIssueGradeRepository = ossIssueGradeRepository;
        this.ossIssueGradeSaver = ossIssueGradeSaver;
        this.gitHubClient = gitHubClient;
        this.issueGrader = issueGrader;
        this.lockProvider = lockProvider;
        this.clock = clock;
        this.maxIssues = maxIssues;
        this.maxDuration = maxDuration;
        this.maxFailures = maxFailures;
        this.gradingLockAtMostFor = maxDuration.plus(GRADING_LOCK_MARGIN);
    }

    public OssIssueGradingRunResult gradeRepo(Long repoId) {
        OssRepo repo = ossRepoRepository.findByIdAndStatus(repoId, OssRepoStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND));
        SimpleLock gradingLock = acquireGradingLock(repo);
        try {
            return gradeHoldingLock(repo);
        } finally {
            releaseGradingLock(gradingLock, repo);
        }
    }

    private SimpleLock acquireGradingLock(OssRepo repo) {
        LockConfiguration lockConfiguration = new LockConfiguration(clock.instant(),
                GRADING_LOCK_NAME_PREFIX + repo.getId(), gradingLockAtMostFor, Duration.ZERO);
        return lockProvider.lock(lockConfiguration).orElseThrow(() -> {
            log.info("오픈소스 레포 {} 이슈 판정이 이미 진행 중이라 시작하지 않음", repo.getFullName());
            return new BusinessException(ErrorCode.OSS_ISSUE_GRADING_IN_PROGRESS);
        });
    }

    private void releaseGradingLock(SimpleLock lock, OssRepo repo) {
        try {
            lock.unlock();
        } catch (RuntimeException e) {
            log.warn("오픈소스 레포 {} 이슈 판정 잠금을 풀지 못함, 잡은 지 {}분 뒤 저절로 풀림",
                    repo.getFullName(), gradingLockAtMostFor.toMinutes(), e);
        }
    }

    private OssIssueGradingRunResult gradeHoldingLock(OssRepo repo) {
        Instant startedAt = clock.instant();
        List<OssIssue> candidates = ossIssueRepository.findGradingCandidates(repo.getId(), maxFailures,
                Limit.of(maxIssues + 1));
        List<OssIssue> selected = candidates.subList(0, Math.min(candidates.size(), maxIssues));
        boolean moreLeft = candidates.size() > selected.size();
        Tally tally = new Tally();
        Stop stop = gradeEach(repo, selected, moreLeft, startedAt, tally);
        OssIssueGradingRunResult result = tally.toResult(stop, selected.size());
        logRun(repo, result, stop);
        return result;
    }

    private Stop gradeEach(OssRepo repo, List<OssIssue> selected, boolean moreLeft, Instant startedAt,
                           Tally tally) {
        for (OssIssue issue : selected) {
            if (isInterrupted()) {
                return Stop.betweenIssues(StopReason.INTERRUPTED, INTERRUPTED_CAUSE);
            }
            if (ranOutOfTime(startedAt)) {
                return Stop.betweenIssues(StopReason.TIME_LIMIT, NONE);
            }
            tally.countStarted();
            Optional<Stop> stopped = gradeOne(repo, issue, startedAt, tally);
            if (stopped.isPresent()) {
                return stopped.get();
            }
        }
        return Stop.betweenIssues(moreLeft ? StopReason.ISSUE_LIMIT : StopReason.COMPLETED, NONE);
    }

    private boolean ranOutOfTime(Instant startedAt) {
        return Duration.between(startedAt, clock.instant()).compareTo(maxDuration) >= 0;
    }

    private Optional<Stop> gradeOne(OssRepo repo, OssIssue issue, Instant startedAt, Tally tally) {
        GitHubIssueLookup lookup;
        try {
            lookup = gitHubClient.findIssue(repo.getGithubId(), issue.getNumber());
        } catch (GitHubClientException e) {
            return failedOnGitHub(issue, e, tally);
        }
        tally.githubAnswered();
        Optional<OssIssueUngradableReason> ungradable = reasonNotToGrade(issue, lookup);
        if (ungradable.isPresent()) {
            countUngradable(issue, ungradable.get(), NONE, tally);
            return Optional.empty();
        }
        GitHubIssueDetail detail = lookup.issue();
        String sourceHash = OssIssueBodyHash.of(detail.body());
        if (ossIssueGradeRepository.existsByIssueIdAndSourceHash(issue.getId(), sourceHash)) {
            tally.countSameBody();
            logIssue(issue, Outcome.SAME_BODY, NONE, NONE, NONE);
            return Optional.empty();
        }
        if (issue.gradingFailedAtLeast(maxFailures, sourceHash)) {
            tally.countFailureLimit();
            logIssue(issue, Outcome.FAILURE_LIMIT, NONE, NONE, NONE);
            return Optional.empty();
        }
        if (ranOutOfTime(startedAt)) {
            return Optional.of(hold(issue, StopReason.TIME_LIMIT, NONE, NONE));
        }
        return grade(issue, detail, sourceHash, tally);
    }

    private static Optional<OssIssueUngradableReason> reasonNotToGrade(OssIssue issue, GitHubIssueLookup lookup) {
        return switch (lookup.status()) {
            case GONE -> Optional.of(OssIssueUngradableReason.GONE);
            case MOVED -> Optional.of(OssIssueUngradableReason.TRANSFERRED);
            case FOUND -> {
                GitHubIssueDetail detail = lookup.issue();
                yield OssIssueUngradableReason.of(detail.githubId() != issue.getGithubIssueId(), detail.open(),
                        detail.pullRequest(), detail.assigneeCount(), detail.authorLogin(),
                        OssIssueAuthorType.from(detail.authorType()));
            }
        };
    }

    private Optional<Stop> failedOnGitHub(OssIssue issue, GitHubClientException failure, Tally tally) {
        String status = statusOf(failure);
        if (isInterrupted()) {
            return Optional.of(hold(issue, StopReason.INTERRUPTED, status, INTERRUPTED_CAUSE));
        }
        return switch (failure.getReason()) {
            case REJECTED -> {
                tally.githubAnswered();
                countUngradable(issue, OssIssueUngradableReason.BLOCKED, status, tally);
                yield Optional.empty();
            }
            case NOT_CONFIGURED ->
                    Optional.of(hold(issue, StopReason.GITHUB_NOT_CONFIGURED, status, failure.getMessage()));
            case UNAUTHORIZED ->
                    Optional.of(hold(issue, StopReason.GITHUB_UNAUTHORIZED, status, failure.getMessage()));
            case RATE_LIMITED ->
                    Optional.of(hold(issue, StopReason.GITHUB_RATE_LIMITED, status, failure.getMessage()));
            case UNAVAILABLE, INVALID_RESPONSE, REDIRECT_REFUSED -> outageOnGitHub(issue, failure, status, tally);
        };
    }

    private Optional<Stop> outageOnGitHub(OssIssue issue, GitHubClientException failure, String status, Tally tally) {
        if (tally.githubFailedToAnswer() >= GITHUB_OUTAGES_IN_A_ROW_TO_STOP) {
            return Optional.of(hold(issue, StopReason.GITHUB_UNAVAILABLE, status, failure.getMessage()));
        }
        tally.countGitHubSkipped();
        logIssue(issue, Outcome.GITHUB_SKIPPED, failure.getReason().name(), status, NONE);
        return Optional.empty();
    }

    private static String statusOf(GitHubClientException failure) {
        OptionalInt status = failure.getStatusCode();
        return status.isPresent() ? String.valueOf(status.getAsInt()) : NONE;
    }

    private void countUngradable(OssIssue issue, OssIssueUngradableReason reason, String status, Tally tally) {
        ossIssueGradeSaver.recordFailure(issue.getId(), issue.getBodyHash());
        tally.countUngradable(reason);
        logIssue(issue, Outcome.UNGRADABLE, reason.name(), status, NONE);
    }

    private Optional<Stop> grade(OssIssue issue, GitHubIssueDetail detail, String sourceHash, Tally tally) {
        IssueGradingResult result;
        try {
            result = issueGrader.grade(detail.title(), detail.body(), detail.labels());
        } catch (IssueGradingException e) {
            return failedToGrade(issue, sourceHash, e, tally);
        }
        save(issue, result, sourceHash, tally);
        return Optional.empty();
    }

    private Optional<Stop> failedToGrade(OssIssue issue, String sourceHash, IssueGradingException failure,
                                         Tally tally) {
        Reason reason = failure.getReason();
        if (isInterrupted()) {
            return Optional.of(hold(issue, StopReason.INTERRUPTED, NONE, INTERRUPTED_CAUSE));
        }
        return switch (reason) {
            case REFUSED, TRUNCATED, INVALID_OUTPUT, INVALID_INPUT -> {
                ossIssueGradeSaver.recordFailure(issue.getId(), sourceHash);
                tally.countFailure(reason);
                logIssue(issue, Outcome.FAILED, reason.name(), NONE, NONE);
                yield Optional.empty();
            }
            case UNAVAILABLE -> Optional.of(hold(issue, StopReason.GRADER_UNAVAILABLE, NONE, failure.getMessage()));
            case REJECTED -> Optional.of(hold(issue, StopReason.GRADER_REJECTED, NONE, failure.getMessage()));
        };
    }

    private void save(OssIssue issue, IssueGradingResult result, String sourceHash, Tally tally) {
        try {
            saveRetryingOnce(issue, result, sourceHash);
        } catch (DataIntegrityViolationException e) {
            if (ossIssueRepository.existsById(issue.getId())) {
                throw e;
            }
            tally.countUngradable(OssIssueUngradableReason.DELETED);
            logIssue(issue, Outcome.UNGRADABLE, OssIssueUngradableReason.DELETED.name(), NONE, result.model());
            return;
        }
        tally.countGraded();
        logIssue(issue, Outcome.GRADED, NONE, NONE, result.model());
    }

    private void saveRetryingOnce(OssIssue issue, IssueGradingResult result, String sourceHash) {
        try {
            ossIssueGradeSaver.save(issue, result.verdict(), result.model(), sourceHash);
        } catch (PessimisticLockingFailureException firstFailure) {
            log.info("오픈소스 이슈 {} 판정 저장이 다른 쓰기와 겹쳐({}) 한 번 더 저장함",
                    issue.getId(), firstFailure.getClass().getSimpleName());
            try {
                ossIssueGradeSaver.save(issue, result.verdict(), result.model(), sourceHash);
            } catch (RuntimeException again) {
                again.addSuppressed(firstFailure);
                throw again;
            }
        }
    }

    private static Stop hold(OssIssue issue, StopReason reason, String status, String cause) {
        logIssue(issue, Outcome.HELD, reason.name(), status, NONE);
        return Stop.holdingIssue(reason, cause);
    }

    private static boolean isInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    private static void logIssue(OssIssue issue, Outcome outcome, String reason, String status, String model) {
        log.info("오픈소스 이슈 판정 issueId={} number={} outcome={} reason={} status={} model={} criteria={}",
                issue.getId(), issue.getNumber(), outcome, reason, status, model, OssIssueGradingCriteria.VERSION);
    }

    private static void logRun(OssRepo repo, OssIssueGradingRunResult result, Stop stop) {
        String format = "오픈소스 레포 {} 이슈 판정: 멈춘 이유 {}, 고름 {}, 판정 {}, 보류 {}, 남김 {}, 같은 본문 {}, "
                + "실패 상한 {}, GitHub 장애로 건너뜀 {}, 판정 불가 {}, 판정 실패 {}, 원인 {}";
        Object[] arguments = {repo.getFullName(), result.stopReason(), result.selected(), result.graded(),
                result.held(), result.notStarted(), result.sameBodySkipped(), result.failureLimitSkipped(),
                result.githubSkipped(), result.ungradable(), result.failed(), stop.cause()};
        if (EXPECTED_STOPS.contains(result.stopReason())) {
            log.info(format, arguments);
            return;
        }
        log.warn(format, arguments);
    }

    private record Stop(StopReason reason, boolean holdsIssue, String cause) {

        static Stop betweenIssues(StopReason reason, String cause) {
            return new Stop(reason, false, cause);
        }

        static Stop holdingIssue(StopReason reason, String cause) {
            return new Stop(reason, true, cause);
        }
    }

    private static final class Tally {

        private final Map<OssIssueUngradableReason, Integer> ungradable =
                new EnumMap<>(OssIssueUngradableReason.class);
        private final Map<Reason, Integer> failed = new EnumMap<>(Reason.class);
        private int started;
        private int graded;
        private int sameBodySkipped;
        private int failureLimitSkipped;
        private int githubSkipped;
        private int githubOutagesInARow;

        void countStarted() {
            started++;
        }

        void githubAnswered() {
            githubOutagesInARow = 0;
        }

        int githubFailedToAnswer() {
            return ++githubOutagesInARow;
        }

        void countGitHubSkipped() {
            githubSkipped++;
        }

        void countGraded() {
            graded++;
        }

        void countSameBody() {
            sameBodySkipped++;
        }

        void countFailureLimit() {
            failureLimitSkipped++;
        }

        void countUngradable(OssIssueUngradableReason reason) {
            ungradable.merge(reason, 1, Integer::sum);
        }

        void countFailure(Reason reason) {
            failed.merge(reason, 1, Integer::sum);
        }

        OssIssueGradingRunResult toResult(Stop stop, int selected) {
            return new OssIssueGradingRunResult(stop.reason(), selected, graded, stop.holdsIssue() ? 1 : 0,
                    selected - started, sameBodySkipped, failureLimitSkipped, githubSkipped, ungradable, failed);
        }
    }
}
