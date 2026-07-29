package uhsuhjupjup.backend.article.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uhsuhjupjup.backend.article.application.dto.ArticleDetailResult;
import uhsuhjupjup.backend.article.application.dto.ArticlePageResult;
import uhsuhjupjup.backend.article.application.dto.ArticleSummaryResult;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private final ArticleRepository articleRepository;
    private final ArticleKeywordRepository articleKeywordRepository;

    public ArticlePageResult search(Long blogId, Collection<Long> keywordIds, Collection<Long> topicIds, String q, Integer page, Integer size) {
        int pageNumber = (page == null || page < 1) ? 0 : page - 1;
        boolean allKeywords = keywordIds == null || keywordIds.isEmpty();
        boolean allTopics = topicIds == null || topicIds.isEmpty();
        Collection<Long> effectiveKeywordIds = allKeywords ? List.of(0L) : keywordIds;
        Collection<Long> effectiveTopicIds = allTopics ? List.of(0L) : topicIds;
        Page<Article> result = articleRepository.search(
                blogId, allKeywords, effectiveKeywordIds, allTopics, effectiveTopicIds,
                trimToNull(q), PageRequest.of(pageNumber, clampSize(size)));

        Map<Long, List<String>> keywordsByArticle = result.isEmpty() ? Map.of()
                : articleKeywordRepository
                        .findWithKeywordByArticleIdIn(result.getContent().stream().map(Article::getId).toList()).stream()
                        .collect(Collectors.groupingBy(
                                ak -> ak.getArticle().getId(),
                                Collectors.mapping(ak -> ak.getKeyword().getName(), Collectors.toList())));
        List<ArticleSummaryResult> content = result.getContent().stream()
                .map(article -> new ArticleSummaryResult(
                        article, keywordsByArticle.getOrDefault(article.getId(), List.of())))
                .toList();

        return new ArticlePageResult(content, result.getNumber() + 1, result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext(), result.hasPrevious());
    }

    public ArticleDetailResult getDetail(Long articleId) {
        Article article = articleRepository.findWithBlogById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        return new ArticleDetailResult(article, articleKeywordRepository.findWithKeywordByArticleId(articleId));
    }

    private int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String trimToNull(String q) {
        return StringUtils.hasText(q) ? q.trim() : null;
    }
}
