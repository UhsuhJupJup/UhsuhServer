package uhsuhjupjup.backend.collection.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleUrlTest {

    @Test
    void normalize_stripsTrackingParams() {
        assertThat(ArticleUrl.normalize("https://a.com/p?utm_source=rss&utm_medium=feed"))
                .isEqualTo("https://a.com/p");
    }

    @Test
    void normalize_keepsNonTrackingQuery() {
        assertThat(ArticleUrl.normalize("https://a.com/p?id=42&utm_source=rss"))
                .isEqualTo("https://a.com/p?id=42");
    }

    @Test
    void normalize_stripsFragment() {
        assertThat(ArticleUrl.normalize("https://a.com/p#comments"))
                .isEqualTo("https://a.com/p");
    }

    @Test
    void normalize_lowercasesHost_andRemovesDefaultPort() {
        assertThat(ArticleUrl.normalize("https://Example.COM:443/Path"))
                .isEqualTo("https://example.com/Path");
    }

    @Test
    void normalize_preservesPathCaseAndTrailingSlash() {
        assertThat(ArticleUrl.normalize("https://a.com/Path/"))
                .isEqualTo("https://a.com/Path/");
    }

    @Test
    void normalize_whenUnparseable_returnsTrimmed() {
        assertThat(ArticleUrl.normalize("  not a url  ")).isEqualTo("not a url");
    }
}
