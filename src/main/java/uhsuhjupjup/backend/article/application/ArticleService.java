package uhsuhjupjup.backend.article.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uhsuhjupjup.backend.article.application.dto.ArticleDetailResult;
import uhsuhjupjup.backend.article.application.dto.ArticleSummaryResult;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ArticleRepository articleRepository;
    private final ArticleKeywordRepository articleKeywordRepository;

    public List<ArticleSummaryResult> search(Long blogId, Long keywordId, Long topicId, String q, Integer limit) {
        List<Article> articles = articleRepository.search(
                blogId, keywordId, topicId, trimToNull(q), PageRequest.of(0, clampLimit(limit)));
        if (articles.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> keywordsByArticle = articleKeywordRepository
                .findWithKeywordByArticleIdIn(articles.stream().map(Article::getId).toList()).stream()
                .collect(Collectors.groupingBy(
                        ak -> ak.getArticle().getId(),
                        Collectors.mapping(ak -> ak.getKeyword().getName(), Collectors.toList())));
        return articles.stream()
                .map(article -> new ArticleSummaryResult(
                        article, keywordsByArticle.getOrDefault(article.getId(), List.of())))
                .toList();
    }

    public ArticleDetailResult getDetail(Long articleId) {
        Article article = articleRepository.findWithBlogById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        return new ArticleDetailResult(article, articleKeywordRepository.findWithKeywordByArticleId(articleId));
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String trimToNull(String q) {
        return StringUtils.hasText(q) ? q.trim() : null;
    }
}
