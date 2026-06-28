package uhsuhjupjup.backend.collection.application;

public class FeedFetchException extends RuntimeException {

    private final String rssUrl;
    private final boolean transientFailure;

    private FeedFetchException(String rssUrl, boolean transientFailure, Throwable cause) {
        super("피드 수집 실패: " + rssUrl, cause);
        this.rssUrl = rssUrl;
        this.transientFailure = transientFailure;
    }

    public static FeedFetchException transientFailure(String rssUrl, Throwable cause) {
        return new FeedFetchException(rssUrl, true, cause);
    }

    public static FeedFetchException permanent(String rssUrl, Throwable cause) {
        return new FeedFetchException(rssUrl, false, cause);
    }

    public String getRssUrl() {
        return rssUrl;
    }

    public boolean isTransient() {
        return transientFailure;
    }
}
