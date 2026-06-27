package uhseojupjup.backend.blog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhseojupjup.backend.blog.domain.Blog;
import uhseojupjup.backend.blog.infra.BlogRepository;
import uhseojupjup.backend.common.exception.BusinessException;
import uhseojupjup.backend.common.exception.ErrorCode;

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
}
