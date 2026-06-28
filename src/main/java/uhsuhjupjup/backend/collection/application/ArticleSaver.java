package uhsuhjupjup.backend.collection.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.collection.application.dto.FetchedArticle;
import uhsuhjupjup.backend.collection.domain.ArticleUrl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ArticleSaver {

    private final ArticleRepository articleRepository;

    @Transactional
    public int saveNew(Blog blog, List<FetchedArticle> fetched) {
        Map<String, FetchedArticle> candidates = dedupeByUrl(fetched);
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<String> existing = Set.copyOf(articleRepository.findExistingUrls(candidates.keySet()));
        List<Article> toSave = candidates.values().stream()
                .filter(candidate -> !existing.contains(candidate.url()))
                .map(candidate -> Article.create(blog, candidate.title(), candidate.url(), publishedAtOf(candidate)))
                .toList();
        articleRepository.saveAll(toSave);
        return toSave.size();
    }

    private Map<String, FetchedArticle> dedupeByUrl(List<FetchedArticle> fetched) {
        Map<String, FetchedArticle> byUrl = new LinkedHashMap<>();
        for (FetchedArticle article : fetched) {
            String normalized = ArticleUrl.normalize(article.url());
            if (normalized == null || normalized.isBlank()) {
                continue;
            }
            byUrl.putIfAbsent(normalized, withUrl(article, normalized));
        }
        return byUrl;
    }

    private FetchedArticle withUrl(FetchedArticle article, String url) {
        return new FetchedArticle(article.title(), url, article.publishedAt());
    }

    private LocalDateTime publishedAtOf(FetchedArticle article) {
        return article.publishedAt() != null ? article.publishedAt() : LocalDateTime.now();
    }
}
