package uhsuhjupjup.backend.archive.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.archive.application.dto.BookmarkedArticleResult;
import uhsuhjupjup.backend.archive.application.dto.BookmarkedArticlesResult;
import uhsuhjupjup.backend.archive.domain.Bookmark;
import uhsuhjupjup.backend.archive.infra.BookmarkRepository;
import uhsuhjupjup.backend.article.application.dto.ArticleSummaryResult;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private static final int MAX_SIZE = 100;

    private final BookmarkRepository bookmarkRepository;
    private final ArticleRepository articleRepository;
    private final ArticleKeywordRepository articleKeywordRepository;

    @Transactional
    public void addBookmark(Member member, Long articleId) {
        if (bookmarkRepository.existsByMemberIdAndArticleId(member.getId(), articleId)) {
            return;
        }
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        bookmarkRepository.save(Bookmark.of(member, article));
    }

    @Transactional
    public void removeBookmark(Long memberId, Long articleId) {
        bookmarkRepository.deleteByMemberIdAndArticleId(memberId, articleId);
    }

    public BookmarkedArticlesResult getBookmarks(Long memberId) {
        List<Bookmark> bookmarks = bookmarkRepository
                .findRecentWithArticleByMemberId(memberId, PageRequest.of(0, MAX_SIZE));

        Map<Long, List<String>> keywordsByArticle = keywordsByArticle(bookmarks);

        List<BookmarkedArticleResult> content = bookmarks.stream()
                .map(bookmark -> new BookmarkedArticleResult(
                        new ArticleSummaryResult(
                                bookmark.getArticle(),
                                keywordsByArticle.getOrDefault(bookmark.getArticle().getId(), List.of())),
                        bookmark.getCreatedAt()))
                .toList();

        return new BookmarkedArticlesResult(content);
    }

    private Map<Long, List<String>> keywordsByArticle(List<Bookmark> bookmarks) {
        if (bookmarks.isEmpty()) {
            return Map.of();
        }
        List<Long> articleIds = bookmarks.stream()
                .map(bookmark -> bookmark.getArticle().getId())
                .toList();
        return articleKeywordRepository.findWithKeywordByArticleIdIn(articleIds).stream()
                .collect(Collectors.groupingBy(
                        articleKeyword -> articleKeyword.getArticle().getId(),
                        Collectors.mapping(articleKeyword -> articleKeyword.getKeyword().getName(),
                                Collectors.toList())));
    }
}
