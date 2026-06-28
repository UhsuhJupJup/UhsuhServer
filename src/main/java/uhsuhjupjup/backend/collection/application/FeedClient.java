package uhsuhjupjup.backend.collection.application;

import uhsuhjupjup.backend.collection.application.dto.FetchedArticle;

import java.util.List;

public interface FeedClient {

    List<FetchedArticle> fetch(String rssUrl);
}
