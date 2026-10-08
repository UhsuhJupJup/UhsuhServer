package uhsuhjupjup.backend.oss.pipeline.grading.domain;

import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssueAuthorType;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter.ExclusionReason;

import java.util.Optional;

public enum OssIssueUngradableReason {
    GONE,
    TRANSFERRED,
    BLOCKED,
    ID_MISMATCH,
    CLOSED,
    PULL_REQUEST,
    BOT_AUTHOR,
    ASSIGNED,
    DELETED;

    public static Optional<OssIssueUngradableReason> of(boolean anotherIssue, boolean open, boolean pullRequest,
                                                        int assigneeCount, String authorLogin,
                                                        OssIssueAuthorType authorType) {
        if (anotherIssue) {
            return Optional.of(ID_MISMATCH);
        }
        if (!open) {
            return Optional.of(CLOSED);
        }
        return OssIssuePrefilter.reasonToExclude(pullRequest, assigneeCount, authorLogin, authorType)
                .map(OssIssueUngradableReason::from);
    }

    private static OssIssueUngradableReason from(ExclusionReason reason) {
        return switch (reason) {
            case PULL_REQUEST -> PULL_REQUEST;
            case BOT_AUTHOR -> BOT_AUTHOR;
            case ASSIGNED -> ASSIGNED;
        };
    }
}
