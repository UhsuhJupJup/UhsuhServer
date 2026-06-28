package uhsuhjupjup.backend.collection.application;

import uhsuhjupjup.backend.collection.application.dto.FetchedArticle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeFeedClient implements FeedClient {

    private final Map<String, List<FetchedArticle>> feeds = new HashMap<>();
    private final Map<String, RuntimeException> failures = new HashMap<>();

    public void willReturn(String rssUrl, List<FetchedArticle> articles) {
        feeds.put(rssUrl, articles);
    }

    public void willThrow(String rssUrl, RuntimeException exception) {
        failures.put(rssUrl, exception);
    }

    @Override
    public List<FetchedArticle> fetch(String rssUrl) {
        RuntimeException failure = failures.get(rssUrl);
        if (failure != null) {
            throw failure;
        }
        return feeds.getOrDefault(rssUrl, List.of());
    }
}
