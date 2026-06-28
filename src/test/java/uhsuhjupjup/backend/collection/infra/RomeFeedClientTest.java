package uhsuhjupjup.backend.collection.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uhsuhjupjup.backend.collection.application.FeedFetchException;
import uhsuhjupjup.backend.collection.application.dto.FetchedArticle;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RomeFeedClientTest {

    private static final String RSS_URL = "https://blog.example.com/rss";

    private MockRestServiceServer server;
    private RomeFeedClient feedClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        feedClient = new RomeFeedClient(builder.build());
    }

    @Test
    void fetch_parsesRss2() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <title>Example</title>
                  <item>
                    <title>첫 글</title>
                    <link>https://blog.example.com/1</link>
                    <pubDate>Mon, 15 Jun 2026 11:00:00 +0900</pubDate>
                  </item>
                </channel></rss>
                """;
        server.expect(requestTo(RSS_URL)).andRespond(withSuccess(rss, MediaType.APPLICATION_XML));

        List<FetchedArticle> result = feedClient.fetch(RSS_URL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("첫 글");
        assertThat(result.get(0).url()).isEqualTo("https://blog.example.com/1");
        assertThat(result.get(0).publishedAt()).isNotNull();
    }

    @Test
    void fetch_parsesAtom_andUsesUpdatedWhenNoPublished() {
        String atom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Example</title>
                  <entry>
                    <title>Atom 글</title>
                    <link href="https://blog.example.com/2"/>
                    <updated>2026-06-15T11:00:00+09:00</updated>
                  </entry>
                </feed>
                """;
        server.expect(requestTo(RSS_URL))
                .andRespond(withSuccess(atom, MediaType.valueOf("application/atom+xml")));

        List<FetchedArticle> result = feedClient.fetch(RSS_URL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Atom 글");
        assertThat(result.get(0).url()).isEqualTo("https://blog.example.com/2");
        assertThat(result.get(0).publishedAt()).isNotNull();
    }

    @Test
    void fetch_skipsEntriesWithoutTitleOrLink() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item><link>https://blog.example.com/notitle</link></item>
                  <item><title>링크 없음</title></item>
                  <item><title>정상</title><link>https://blog.example.com/ok</link></item>
                </channel></rss>
                """;
        server.expect(requestTo(RSS_URL)).andRespond(withSuccess(rss, MediaType.APPLICATION_XML));

        List<FetchedArticle> result = feedClient.fetch(RSS_URL);

        assertThat(result).extracting(FetchedArticle::url)
                .containsExactly("https://blog.example.com/ok");
    }

    @Test
    void fetch_when404_throwsPermanent() {
        server.expect(requestTo(RSS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> feedClient.fetch(RSS_URL))
                .isInstanceOf(FeedFetchException.class)
                .extracting(e -> ((FeedFetchException) e).isTransient())
                .isEqualTo(false);
    }

    @Test
    void fetch_when500_throwsTransient() {
        server.expect(requestTo(RSS_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> feedClient.fetch(RSS_URL))
                .isInstanceOf(FeedFetchException.class)
                .extracting(e -> ((FeedFetchException) e).isTransient())
                .isEqualTo(true);
    }

    @Test
    void fetch_whenNotAFeed_throwsPermanent() {
        server.expect(requestTo(RSS_URL))
                .andRespond(withSuccess("not a feed at all", MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> feedClient.fetch(RSS_URL))
                .isInstanceOf(FeedFetchException.class)
                .extracting(e -> ((FeedFetchException) e).isTransient())
                .isEqualTo(false);
    }
}
