package uhsuhjupjup.backend.support;

import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.blog.domain.Blog;

import java.time.LocalDateTime;

public final class ArticleFixture {

    private ArticleFixture() {
    }

    public static Article article(Long id, Blog blog, String title, String url, LocalDateTime publishedAt) {
        Article article = Article.create(blog, title, url, publishedAt);
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }
}
