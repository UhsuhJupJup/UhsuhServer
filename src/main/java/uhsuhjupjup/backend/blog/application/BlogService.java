package uhsuhjupjup.backend.blog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogService {

    private final BlogRepository blogRepository;

    public List<Blog> findActive() {
        return blogRepository.findByActiveTrueOrderByIdAsc();
    }

    public Blog getDetail(Long blogId) {
        return blogRepository.findById(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
    }

    public List<Blog> findAllForAdmin() {
        return blogRepository.findAllByOrderByIdAsc();
    }

    @Transactional
    public Blog add(String name, String domain, String rssUrl, String logoUrl) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(domain) || !StringUtils.hasText(rssUrl)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (blogRepository.existsByDomain(domain)) {
            throw new BusinessException(ErrorCode.BLOG_ALREADY_EXISTS);
        }
        return blogRepository.save(Blog.create(name, domain, rssUrl, normalizeLogoUrl(logoUrl)));
    }

    @Transactional
    public void deactivate(Long blogId) {
        getDetail(blogId).deactivate();
    }

    @Transactional
    public void activate(Long blogId) {
        getDetail(blogId).activate();
    }

    @Transactional
    public Blog update(Long blogId, String name, String rssUrl, String logoUrl) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(rssUrl)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Blog blog = getDetail(blogId);
        blog.update(name, rssUrl, normalizeLogoUrl(logoUrl));
        return blog;
    }

    private String normalizeLogoUrl(String logoUrl) {
        return StringUtils.hasText(logoUrl) ? logoUrl.trim() : null;
    }
}
