package uhsuhjupjup.backend.blog.ui.dto;

import uhsuhjupjup.backend.blog.domain.Blog;

public record BlogResponse(Long id, String name, String domain, String logoUrl) {

    public static BlogResponse from(Blog blog) {
        return new BlogResponse(blog.getId(), blog.getName(), blog.getDomain(), blog.getLogoUrl());
    }
}
