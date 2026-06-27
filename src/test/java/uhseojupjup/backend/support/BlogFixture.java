package uhseojupjup.backend.support;

import org.springframework.test.util.ReflectionTestUtils;
import uhseojupjup.backend.blog.domain.Blog;

public final class BlogFixture {

    private BlogFixture() {
    }

    public static Blog blog(Long id, String name, String domain) {
        Blog blog = Blog.create(name, domain, "https://" + domain + "/rss.xml");
        ReflectionTestUtils.setField(blog, "id", id);
        return blog;
    }
}
