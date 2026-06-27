package uhseojupjup.backend.blog.ui.dto;

import uhseojupjup.backend.blog.domain.Blog;

public record BlogResponse(Long id, String name, String domain) {

    public static BlogResponse from(Blog blog) {
        return new BlogResponse(blog.getId(), blog.getName(), blog.getDomain());
    }
}
