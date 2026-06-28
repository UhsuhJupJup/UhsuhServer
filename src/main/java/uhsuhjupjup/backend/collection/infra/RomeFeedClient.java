package uhsuhjupjup.backend.collection.infra;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import uhsuhjupjup.backend.collection.application.FeedClient;
import uhsuhjupjup.backend.collection.application.FeedFetchException;
import uhsuhjupjup.backend.collection.application.dto.FetchedArticle;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class RomeFeedClient implements FeedClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestClient feedRestClient;

    @Override
    public List<FetchedArticle> fetch(String rssUrl) {
        SyndFeed feed = fetchFeed(rssUrl);
        return feed.getEntries().stream()
                .map(this::toFetchedArticle)
                .flatMap(Optional::stream)
                .toList();
    }

    private SyndFeed fetchFeed(String rssUrl) {
        try {
            return feedRestClient.get()
                    .uri(URI.create(rssUrl))
                    .accept(MediaType.APPLICATION_ATOM_XML,
                            MediaType.valueOf("application/rss+xml"),
                            MediaType.APPLICATION_XML)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (!status.is2xxSuccessful()) {
                            throw classifyStatus(rssUrl, status);
                        }
                        try (XmlReader reader = new XmlReader(response.getBody(),
                                response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))) {
                            return new SyndFeedInput().build(reader);
                        } catch (FeedException | IllegalArgumentException e) {
                            throw FeedFetchException.permanent(rssUrl, e);
                        }
                    });
        } catch (ResourceAccessException e) {
            throw FeedFetchException.transientFailure(rssUrl, e);
        }
    }

    private FeedFetchException classifyStatus(String rssUrl, HttpStatusCode status) {
        IllegalStateException cause = new IllegalStateException("예상치 못한 응답 상태: " + status);
        return status.is4xxClientError()
                ? FeedFetchException.permanent(rssUrl, cause)
                : FeedFetchException.transientFailure(rssUrl, cause);
    }

    private Optional<FetchedArticle> toFetchedArticle(SyndEntry entry) {
        String url = entry.getLink();
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String title = entry.getTitle() == null ? "" : entry.getTitle().strip();
        if (title.isBlank()) {
            return Optional.empty();
        }
        Date published = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
        LocalDateTime publishedAt = published == null ? null
                : LocalDateTime.ofInstant(published.toInstant(), KST);
        return Optional.of(new FetchedArticle(title, url, publishedAt));
    }
}
