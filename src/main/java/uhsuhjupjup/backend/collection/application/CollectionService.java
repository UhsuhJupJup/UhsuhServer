package uhsuhjupjup.backend.collection.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.collection.application.dto.CollectionResult;
import uhsuhjupjup.backend.collection.application.dto.FetchedArticle;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final BlogRepository blogRepository;
    private final FeedClient feedClient;
    private final ArticleSaver articleSaver;

    public CollectionResult collectAll() {
        List<Blog> blogs = blogRepository.findByActiveTrueOrderByIdAsc();
        int succeeded = 0;
        int transientFailed = 0;
        int permanentFailed = 0;
        int newArticles = 0;
        for (Blog blog : blogs) {
            try {
                List<FetchedArticle> fetched = feedClient.fetch(blog.getRssUrl());
                newArticles += articleSaver.saveNew(blog, fetched);
                succeeded++;
            } catch (FeedFetchException e) {
                if (e.isTransient()) {
                    transientFailed++;
                } else {
                    permanentFailed++;
                }
                log.warn("수집 실패 blogId={} rssUrl={} 사유={}", blog.getId(), blog.getRssUrl(), e.getMessage());
            }
        }
        CollectionResult result = new CollectionResult(
                blogs.size(), succeeded, transientFailed, permanentFailed, newArticles);
        log.info("수집 완료 {}", result);
        return result;
    }
}
