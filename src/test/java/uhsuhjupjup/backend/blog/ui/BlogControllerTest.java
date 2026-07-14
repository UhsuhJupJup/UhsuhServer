package uhsuhjupjup.backend.blog.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.blog.application.BlogService;
import uhsuhjupjup.backend.support.BlogFixture;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BlogControllerTest {

    @Mock
    private BlogService blogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BlogController(blogService)).build();
    }

    @Test
    void list_returnsBlogs() throws Exception {
        given(blogService.findActive())
                .willReturn(List.of(BlogFixture.blog(1L, "우아한형제들", "techblog.woowahan.com")));

        mockMvc.perform(get("/api/blogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("우아한형제들"))
                .andExpect(jsonPath("$[0].domain").value("techblog.woowahan.com"));
    }

    @Test
    void detail_returnsBlog() throws Exception {
        given(blogService.getDetail(2L)).willReturn(BlogFixture.blog(2L, "토스", "toss.tech"));

        mockMvc.perform(get("/api/blogs/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("토스"))
                .andExpect(jsonPath("$.domain").value("toss.tech"));
    }
}
