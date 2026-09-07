package uhsuhjupjup.backend.blog.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.blog.application.BlogService;
import uhsuhjupjup.backend.blog.ui.dto.BlogDetailResponse;
import uhsuhjupjup.backend.blog.ui.dto.BlogResponse;

import java.util.List;

@RestController
@RequestMapping("/api/blogs")
@RequiredArgsConstructor
public class BlogController implements BlogControllerApi {

    private final BlogService blogService;

    @Override
    @GetMapping
    public List<BlogResponse> list() {
        return blogService.findActive().stream()
                .map(BlogResponse::from)
                .toList();
    }

    @Override
    @GetMapping("/{id}")
    public BlogDetailResponse detail(@PathVariable Long id) {
        return BlogDetailResponse.from(blogService.getDetail(id));
    }
}
