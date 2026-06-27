package uhseojupjup.backend.blog.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import uhseojupjup.backend.blog.domain.Blog;
import uhseojupjup.backend.config.JpaAuditingConfig;
import uhseojupjup.backend.support.MySqlTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class BlogRepositoryTest extends MySqlTestSupport {

    @Autowired
    private BlogRepository blogRepository;

    @Test
    void findByActiveTrueOrderByIdAsc_excludesInactive() {
        blogRepository.save(Blog.create("우아한형제들", "techblog.woowahan.com", "https://techblog.woowahan.com/feed.xml"));
        blogRepository.save(Blog.create("토스", "toss.tech", "https://toss.tech/rss.xml"));
        Blog inactive = Blog.create("비활성", "inactive.example.com", "https://inactive.example.com/rss");
        inactive.deactivate();
        blogRepository.save(inactive);

        assertThat(blogRepository.findByActiveTrueOrderByIdAsc())
                .extracting(Blog::getName)
                .containsExactly("우아한형제들", "토스");
    }

    @Test
    void duplicateDomain_violatesUniqueConstraint() {
        blogRepository.saveAndFlush(Blog.create("토스", "toss.tech", "https://toss.tech/rss.xml"));

        assertThatThrownBy(() -> blogRepository.saveAndFlush(
                Blog.create("토스 복제", "toss.tech", "https://toss.tech/other.xml")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditing_setsCreatedAndUpdatedAt() {
        Blog saved = blogRepository.saveAndFlush(
                Blog.create("토스", "toss.tech", "https://toss.tech/rss.xml"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
