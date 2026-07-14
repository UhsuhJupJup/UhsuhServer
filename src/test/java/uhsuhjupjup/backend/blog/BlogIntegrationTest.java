package uhsuhjupjup.backend.blog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlogIntegrationTest extends MySqlTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlogRepository blogRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Blog toss;

    @BeforeEach
    void setUp() {
        blogRepository.save(Blog.create("우아한형제들", "techblog.woowahan.com", "https://techblog.woowahan.com/feed.xml"));
        toss = blogRepository.save(Blog.create("토스", "toss.tech", "https://toss.tech/rss.xml"));
        Blog inactive = Blog.create("비활성", "inactive.example.com", "https://inactive.example.com/rss");
        inactive.deactivate();
        blogRepository.save(inactive);
    }

    @Test
    void list_returnsActiveOnly() throws Exception {
        mockMvc.perform(get("/api/blogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("우아한형제들"))
                .andExpect(jsonPath("$[1].name").value("토스"));
    }

    @Test
    void detail_returnsBlog() throws Exception {
        mockMvc.perform(get("/api/blogs/" + toss.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("토스"))
                .andExpect(jsonPath("$.domain").value("toss.tech"));
    }

    @Test
    void detail_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/blogs/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BLOG_NOT_FOUND"));
    }
}
