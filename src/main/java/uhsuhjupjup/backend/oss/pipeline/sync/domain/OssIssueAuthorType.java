package uhsuhjupjup.backend.oss.pipeline.sync.domain;

public enum OssIssueAuthorType {
    BOT,
    OTHER;

    private static final String GITHUB_BOT_TYPE = "Bot";

    public static OssIssueAuthorType from(String githubType) {
        return GITHUB_BOT_TYPE.equals(githubType) ? BOT : OTHER;
    }
}
