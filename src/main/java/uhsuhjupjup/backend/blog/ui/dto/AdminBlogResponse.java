package uhsuhjupjup.backend.blog.ui.dto;

import uhsuhjupjup.backend.blog.domain.Blog;

public record AdminBlogResponse(Long id, String name, String domain, String rssUrl, boolean active) {

    public static AdminBlogResponse from(Blog blog) {
        return new AdminBlogResponse(blog.getId(), blog.getName(), blog.getDomain(), blog.getRssUrl(), blog.isActive());
    }
}
