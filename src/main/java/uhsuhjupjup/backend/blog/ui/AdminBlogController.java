package uhsuhjupjup.backend.blog.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.blog.application.BlogService;
import uhsuhjupjup.backend.blog.ui.dto.AdminBlogResponse;
import uhsuhjupjup.backend.blog.ui.dto.BlogCreateRequest;
import uhsuhjupjup.backend.blog.ui.dto.BlogUpdateRequest;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.List;

@RestController
@RequestMapping("/api/admin/blogs")
@RequiredArgsConstructor
public class AdminBlogController {

    private final BlogService blogService;

    @GetMapping
    public List<AdminBlogResponse> list(@AdminMember Member admin) {
        return blogService.findAllForAdmin().stream()
                .map(AdminBlogResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminBlogResponse add(@AdminMember Member admin, @RequestBody BlogCreateRequest request) {
        return AdminBlogResponse.from(blogService.add(request.name(), request.domain(), request.rssUrl(), request.logoUrl()));
    }

    @PatchMapping("/{id}")
    public AdminBlogResponse update(@AdminMember Member admin, @PathVariable Long id,
                                    @RequestBody BlogUpdateRequest request) {
        return AdminBlogResponse.from(blogService.update(id, request.name(), request.rssUrl(), request.logoUrl()));
    }

    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AdminMember Member admin, @PathVariable Long id) {
        blogService.deactivate(id);
    }

    @PatchMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@AdminMember Member admin, @PathVariable Long id) {
        blogService.activate(id);
    }
}
