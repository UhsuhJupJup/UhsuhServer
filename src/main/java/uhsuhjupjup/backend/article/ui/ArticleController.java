package uhsuhjupjup.backend.article.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.article.application.ArticleService;
import uhsuhjupjup.backend.article.ui.dto.ArticleDetailResponse;
import uhsuhjupjup.backend.article.ui.dto.ArticleResponse;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping
    public List<ArticleResponse> list(@RequestParam(required = false) Long topicId,
                                      @RequestParam(required = false) Long keywordId,
                                      @RequestParam(required = false) Long blogId,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) Integer limit) {
        return articleService.search(blogId, keywordId, topicId, q, limit).stream()
                .map(ArticleResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ArticleDetailResponse detail(@PathVariable Long id) {
        return ArticleDetailResponse.from(articleService.getDetail(id));
    }
}
