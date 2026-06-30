package uhsuhjupjup.backend.pipeline.collection.application;

import uhsuhjupjup.backend.pipeline.collection.application.dto.FetchedArticle;

import java.util.List;

public interface FeedClient {

    List<FetchedArticle> fetch(String rssUrl);
}
