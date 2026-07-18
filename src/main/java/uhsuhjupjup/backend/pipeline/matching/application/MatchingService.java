package uhsuhjupjup.backend.pipeline.matching.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.pipeline.collection.application.FeedClient;
import uhsuhjupjup.backend.pipeline.collection.application.dto.FetchedArticle;
import uhsuhjupjup.backend.pipeline.collection.domain.ArticleUrl;
import uhsuhjupjup.backend.pipeline.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private static final int WINDOW_DAYS = 2;

    private final ArticleRepository articleRepository;
    private final KeywordRepository keywordRepository;
    private final KeywordAliasRepository keywordAliasRepository;
    private final FeedClient feedClient;
    private final KeywordClassifier keywordClassifier;
    private final ArticleKeywordSaver articleKeywordSaver;

    public MatchingResult matchRecent() {
        List<Article> articles = articleRepository.findPendingClassificationWithBlog(
                LocalDateTime.now().minusDays(WINDOW_DAYS));
        MatchCatalog catalog = MatchCatalog.from(
                keywordRepository.findAll(), keywordAliasRepository.findAll());

        int articlesTagged = 0;
        int tagsCreated = 0;
        Map<Long, List<Article>> byBlog = articles.stream()
                .collect(Collectors.groupingBy(article -> article.getBlog().getId()));
        for (List<Article> blogArticles : byBlog.values()) {
            Optional<Map<String, String>> bodyByUrl = fetchBodies(blogArticles.get(0).getBlog());
            if (bodyByUrl.isEmpty()) {
                continue;
            }
            for (Article article : blogArticles) {
                try {
                    String body = bodyByUrl.get().get(article.getUrl());
                    List<KeywordMatch> matches = keywordClassifier.classify(article.getTitle(), body, catalog);
                    int created = articleKeywordSaver.recordClassification(
                            article.getId(), matches, LocalDateTime.now());
                    if (created > 0) {
                        articlesTagged++;
                    }
                    tagsCreated += created;
                } catch (Exception e) {
                    log.warn("분류 실패 articleId={} 사유={}", article.getId(), e.getMessage());
                }
            }
        }
        MatchingResult result = new MatchingResult(articles.size(), articlesTagged, tagsCreated);
        log.info("매칭 완료 {}", result);
        return result;
    }

    private Optional<Map<String, String>> fetchBodies(Blog blog) {
        try {
            Map<String, String> bodyByUrl = new HashMap<>();
            for (FetchedArticle fetched : feedClient.fetch(blog.getRssUrl())) {
                if (fetched.body() != null) {
                    bodyByUrl.putIfAbsent(ArticleUrl.normalize(fetched.url()), fetched.body());
                }
            }
            return Optional.of(bodyByUrl);
        } catch (Exception e) {
            log.warn("본문 재fetch 실패 blogId={} 사유={}", blog.getId(), e.getMessage());
            return Optional.empty();
        }
    }
}
