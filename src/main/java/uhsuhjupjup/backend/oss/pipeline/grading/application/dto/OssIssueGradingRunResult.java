package uhsuhjupjup.backend.oss.pipeline.grading.application.dto;

import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueUngradableReason;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public record OssIssueGradingRunResult(
        StopReason stopReason,
        int selected,
        int graded,
        int held,
        int notStarted,
        int sameBodySkipped,
        int failureLimitSkipped,
        int githubSkipped,
        Map<OssIssueUngradableReason, Integer> ungradable,
        Map<Reason, Integer> failed) {

    public enum StopReason {
        COMPLETED,
        ISSUE_LIMIT,
        TIME_LIMIT,
        GRADER_UNAVAILABLE,
        GRADER_REJECTED,
        GITHUB_NOT_CONFIGURED,
        GITHUB_UNAUTHORIZED,
        GITHUB_RATE_LIMITED,
        GITHUB_UNAVAILABLE,
        INTERRUPTED
    }

    private static final Set<Reason> COUNTED_FAILURES =
            EnumSet.of(Reason.REFUSED, Reason.TRUNCATED, Reason.INVALID_OUTPUT, Reason.INVALID_INPUT);

    private static final String UNCOUNTED_FAILURE = "이슈 하나의 실패만 셉니다: ";

    public OssIssueGradingRunResult {
        Map<OssIssueUngradableReason, Integer> everyUngradable = new EnumMap<>(OssIssueUngradableReason.class);
        for (OssIssueUngradableReason reason : OssIssueUngradableReason.values()) {
            everyUngradable.put(reason, ungradable.getOrDefault(reason, 0));
        }
        ungradable = Collections.unmodifiableMap(everyUngradable);
        for (Reason reason : failed.keySet()) {
            if (!COUNTED_FAILURES.contains(reason)) {
                throw new IllegalArgumentException(UNCOUNTED_FAILURE + reason);
            }
        }
        Map<Reason, Integer> everyFailure = new EnumMap<>(Reason.class);
        for (Reason reason : COUNTED_FAILURES) {
            everyFailure.put(reason, failed.getOrDefault(reason, 0));
        }
        failed = Collections.unmodifiableMap(everyFailure);
    }
}
