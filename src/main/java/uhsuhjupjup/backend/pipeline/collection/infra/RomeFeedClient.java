package uhsuhjupjup.backend.pipeline.collection.infra;

import com.rometools.rome.feed.synd.SyndContent;
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
import uhsuhjupjup.backend.pipeline.collection.application.FeedClient;
import uhsuhjupjup.backend.pipeline.collection.application.FeedFetchException;
import uhsuhjupjup.backend.pipeline.collection.application.dto.FetchedArticle;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
class RomeFeedClient implements FeedClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_BODY_CHARS = 4000;
    private static final Pattern HTML_ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos|nbsp);");

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
                            MediaType.APPLICATION_XML,
                            MediaType.TEXT_XML,
                            MediaType.ALL)
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
        return Optional.of(new FetchedArticle(title, url, publishedAt, extractBody(entry)));
    }

    private String extractBody(SyndEntry entry) {
        String raw = rawContent(entry);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = decodeEntities(raw.replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ")
                .strip();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= MAX_BODY_CHARS ? text : text.substring(0, MAX_BODY_CHARS);
    }

    private String decodeEntities(String raw) {
        Matcher matcher = HTML_ENTITY.matcher(raw);
        StringBuilder decoded = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(decodeEntity(matcher.group(1))));
        }
        matcher.appendTail(decoded);
        return decoded.toString();
    }

    private String decodeEntity(String entity) {
        return switch (entity) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> " ";
            default -> {
                int codePoint = entity.startsWith("#x") || entity.startsWith("#X")
                        ? Integer.parseInt(entity.substring(2), 16)
                        : Integer.parseInt(entity.substring(1));
                yield new String(Character.toChars(codePoint));
            }
        };
    }

    private String rawContent(SyndEntry entry) {
        for (SyndContent content : entry.getContents()) {
            if (content.getValue() != null && !content.getValue().isBlank()) {
                return content.getValue();
            }
        }
        return entry.getDescription() == null ? null : entry.getDescription().getValue();
    }
}
