package uhsuhjupjup.backend.collection.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.collection.application.dto.CollectionResult;
import uhsuhjupjup.backend.collection.application.dto.FetchedArticle;
import uhsuhjupjup.backend.support.BlogFixture;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private BlogRepository blogRepository;

    @Mock
    private ArticleSaver articleSaver;

    private FakeFeedClient feedClient;
    private CollectionService collectionService;

    private final Blog blogA = BlogFixture.blog(1L, "A", "a.com");
    private final Blog blogB = BlogFixture.blog(2L, "B", "b.com");
    private final Blog blogC = BlogFixture.blog(3L, "C", "c.com");

    @BeforeEach
    void setUp() {
        feedClient = new FakeFeedClient();
        collectionService = new CollectionService(blogRepository, feedClient, articleSaver);
    }

    @Test
    void collectAll_isolatesFailures_andAggregates() {
        given(blogRepository.findByActiveTrueOrderByIdAsc()).willReturn(List.of(blogA, blogB, blogC));
        feedClient.willReturn(blogA.getRssUrl(), List.of(fetched("A1"), fetched("A2")));
        feedClient.willThrow(blogB.getRssUrl(),
                FeedFetchException.transientFailure(blogB.getRssUrl(), new RuntimeException()));
        feedClient.willThrow(blogC.getRssUrl(),
                FeedFetchException.permanent(blogC.getRssUrl(), new RuntimeException()));
        given(articleSaver.saveNew(eq(blogA), any())).willReturn(2);

        CollectionResult result = collectionService.collectAll();

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.transientFailed()).isEqualTo(1);
        assertThat(result.permanentFailed()).isEqualTo(1);
        assertThat(result.newArticles()).isEqualTo(2);
    }

    @Test
    void collectAll_continuesAfterFailure_savingLaterBlogs() {
        given(blogRepository.findByActiveTrueOrderByIdAsc()).willReturn(List.of(blogB, blogA));
        feedClient.willThrow(blogB.getRssUrl(),
                FeedFetchException.transientFailure(blogB.getRssUrl(), new RuntimeException()));
        feedClient.willReturn(blogA.getRssUrl(), List.of(fetched("A1")));
        given(articleSaver.saveNew(eq(blogA), any())).willReturn(1);

        CollectionResult result = collectionService.collectAll();

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.transientFailed()).isEqualTo(1);
        assertThat(result.newArticles()).isEqualTo(1);
    }

    @Test
    void collectAll_whenNoBlogs_returnsZeroResult() {
        given(blogRepository.findByActiveTrueOrderByIdAsc()).willReturn(List.of());

        CollectionResult result = collectionService.collectAll();

        assertThat(result.total()).isZero();
        assertThat(result.succeeded()).isZero();
        assertThat(result.newArticles()).isZero();
    }

    private FetchedArticle fetched(String title) {
        return new FetchedArticle(title, "https://a.com/" + title, LocalDateTime.now());
    }
}
