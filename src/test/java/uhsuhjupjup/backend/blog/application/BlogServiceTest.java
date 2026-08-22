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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Test
    void add_새_도메인이면_저장한다() {
        given(blogRepository.existsByDomain("toss.tech")).willReturn(false);
        given(blogRepository.save(any(Blog.class))).willAnswer(invocation -> invocation.getArgument(0));

        Blog saved = blogService.add("토스", "toss.tech", "https://toss.tech/rss");

        assertThat(saved.getName()).isEqualTo("토스");
        assertThat(saved.isActive()).isTrue();
        verify(blogRepository).save(any(Blog.class));
    }

    @Test
    void add_도메인이_중복이면_예외() {
        given(blogRepository.existsByDomain("toss.tech")).willReturn(true);

        assertThatThrownBy(() -> blogService.add("토스", "toss.tech", "https://toss.tech/rss"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLOG_ALREADY_EXISTS);
        verify(blogRepository, never()).save(any());
    }

    @Test
    void add_빈값이면_검증오류() {
        assertThatThrownBy(() -> blogService.add("", "toss.tech", "https://toss.tech/rss"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void deactivate_블로그를_비활성화한다() {
        Blog blog = BlogFixture.blog(1L, "토스", "toss.tech");
        given(blogRepository.findById(1L)).willReturn(Optional.of(blog));

        blogService.deactivate(1L);

        assertThat(blog.isActive()).isFalse();
    }

    @Test
    void activate_블로그를_활성화한다() {
        Blog blog = BlogFixture.blog(1L, "토스", "toss.tech");
        blog.deactivate();
        given(blogRepository.findById(1L)).willReturn(Optional.of(blog));

        blogService.activate(1L);

        assertThat(blog.isActive()).isTrue();
    }

    @Test
    void update_이름과_RSS를_수정한다() {
        Blog blog = BlogFixture.blog(1L, "토스", "toss.tech");
        given(blogRepository.findById(1L)).willReturn(Optional.of(blog));

        Blog updated = blogService.update(1L, "토스 테크", "https://toss.tech/feed");

        assertThat(updated.getName()).isEqualTo("토스 테크");
        assertThat(updated.getRssUrl()).isEqualTo("https://toss.tech/feed");
    }

    @Test
    void update_빈값이면_검증오류() {
        assertThatThrownBy(() -> blogService.update(1L, "", "https://toss.tech/feed"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(blogRepository, never()).findById(any());
    }
}
