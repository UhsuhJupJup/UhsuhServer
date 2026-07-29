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

import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping
    public ArticlePageResponse list(@RequestParam(required = false) List<Long> topicIds,
                                    @RequestParam(required = false) List<Long> keywordIds,
                                    @RequestParam(required = false) Long blogId,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        return ArticlePageResponse.from(
                articleService.search(blogId, keywordIds, topicIds, q, page, size));
    }

    @GetMapping("/{id}")
    public ArticleDetailResponse detail(@PathVariable Long id) {
        return ArticleDetailResponse.from(articleService.getDetail(id));
    }
}
