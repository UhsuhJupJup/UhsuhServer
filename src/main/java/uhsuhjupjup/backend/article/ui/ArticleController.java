package uhsuhjupjup.backend.article.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.article.application.ArticleService;
import uhsuhjupjup.backend.article.ui.dto.ArticleDetailResponse;
import uhsuhjupjup.backend.article.ui.dto.ArticlePageResponse;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping
    public ArticlePageResponse list(@RequestParam(required = false) Long topicId,
                                    @RequestParam(required = false) Long keywordId,
                                    @RequestParam(required = false) Long blogId,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        return ArticlePageResponse.from(
                articleService.search(blogId, keywordId, topicId, q, page, size));
    }

    @GetMapping("/{id}")
    public ArticleDetailResponse detail(@PathVariable Long id) {
        return ArticleDetailResponse.from(articleService.getDetail(id));
    }
}
