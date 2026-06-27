package uhsuhjupjup.backend.blog.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.support.BlogFixture;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BlogServiceTest {

    @Mock
    private BlogRepository blogRepository;

    @InjectMocks
    private BlogService blogService;

    @Test
    void findActive_returnsActiveBlogs() {
        List<Blog> blogs = List.of(BlogFixture.blog(1L, "우아한형제들", "techblog.woowahan.com"));
        given(blogRepository.findByActiveTrueOrderByIdAsc()).willReturn(blogs);

        assertThat(blogService.findActive()).isEqualTo(blogs);
    }

    @Test
    void getDetail_returnsBlog() {
        Blog blog = BlogFixture.blog(1L, "토스", "toss.tech");
        given(blogRepository.findById(1L)).willReturn(Optional.of(blog));

        assertThat(blogService.getDetail(1L)).isEqualTo(blog);
    }

    @Test
    void getDetail_whenBlogNotFound_throws() {
        given(blogRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> blogService.getDetail(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLOG_NOT_FOUND);
    }
}
