package uhsuhjupjup.backend.oss.pipeline.sync.domain;

import java.util.Optional;

public final class OssIssuePrefilter {

    public enum ExclusionReason {
        PULL_REQUEST,
        BOT_AUTHOR,
        ASSIGNED
    }

    private static final String BOT_LOGIN_SUFFIX = "[bot]";

    private OssIssuePrefilter() {
    }

    public static Optional<ExclusionReason> reasonToExclude(boolean pullRequest, int assigneeCount,
                                                            String authorLogin, OssIssueAuthorType authorType) {
        if (pullRequest) {
            return Optional.of(ExclusionReason.PULL_REQUEST);
        }
        if (isBot(authorLogin, authorType)) {
            return Optional.of(ExclusionReason.BOT_AUTHOR);
        }
        if (assigneeCount > 0) {
            return Optional.of(ExclusionReason.ASSIGNED);
        }
        return Optional.empty();
    }

    private static boolean isBot(String authorLogin, OssIssueAuthorType authorType) {
        return authorType == OssIssueAuthorType.BOT || endsWithBotSuffix(authorLogin);
    }

    private static boolean endsWithBotSuffix(String authorLogin) {
        return authorLogin != null && authorLogin.endsWith(BOT_LOGIN_SUFFIX);
    }
}
