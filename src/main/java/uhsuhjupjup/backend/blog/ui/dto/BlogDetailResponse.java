package uhsuhjupjup.backend.blog.ui.dto;

import uhsuhjupjup.backend.blog.domain.Blog;

public record BlogDetailResponse(Long id, String name, String domain, String logoUrl) {

    public static BlogDetailResponse from(Blog blog) {
        return new BlogDetailResponse(blog.getId(), blog.getName(), blog.getDomain(), blog.getLogoUrl());
    }
}
