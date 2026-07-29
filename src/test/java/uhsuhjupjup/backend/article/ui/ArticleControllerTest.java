package uhsuhjupjup.backend.article.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.article.application.ArticleService;
import uhsuhjupjup.backend.article.application.dto.ArticleDetailResult;
import uhsuhjupjup.backend.article.application.dto.ArticlePageResult;
import uhsuhjupjup.backend.article.application.dto.ArticleSummaryResult;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.support.ArticleFixture;
import uhsuhjupjup.backend.support.BlogFixture;
import uhsuhjupjup.backend.support.KeywordFixture;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ArticleControllerTest {

    @Mock
    private ArticleService articleService;

    private MockMvc mockMvc;

    private final Blog blog = BlogFixture.blog(1L, "우아한형제들", "techblog.woowahan.com");
    private final Article article = ArticleFixture.article(
            1L, blog, "MySQL 데드락 디버깅 회고", "https://techblog.woowahan.com/12345/",
            LocalDateTime.of(2026, 6, 15, 11, 0));

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ArticleController(articleService)).build();
    }

    @Test
    void list_returnsArticleCards() throws Exception {
        given(articleService.search(null, null, null, null, null, null))
                .willReturn(new ArticlePageResult(
                        List.of(new ArticleSummaryResult(article, List.of("MySQL"))), 1, 10, 1, 1, false, false));

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("MySQL 데드락 디버깅 회고"))
                .andExpect(jsonPath("$.content[0].blog.name").value("우아한형제들"))
                .andExpect(jsonPath("$.content[0].keywords[0]").value("MySQL"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void list_withFilters_passesParamsToService() throws Exception {
        given(articleService.search(2L, null, null, "kafka", 2, 5))
                .willReturn(new ArticlePageResult(List.of(), 2, 5, 0, 0, false, true));

        mockMvc.perform(get("/api/articles")
                        .param("blogId", "2").param("q", "kafka").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void list_withTopicIds_passesToService() throws Exception {
        given(articleService.search(null, null, List.of(1L, 2L), null, null, null))
                .willReturn(new ArticlePageResult(List.of(), 1, 10, 0, 0, false, false));

        mockMvc.perform(get("/api/articles").param("topicIds", "1,2"))
                .andExpect(status().isOk());
    }

    @Test
    void list_withKeywordIds_passesToService() throws Exception {
        given(articleService.search(null, List.of(3L, 4L), null, null, null, null))
                .willReturn(new ArticlePageResult(List.of(), 1, 10, 0, 0, false, false));

        mockMvc.perform(get("/api/articles").param("keywordIds", "3,4"))
                .andExpect(status().isOk());
    }

    @Test
    void detail_returnsArticleWithMatchedKeywords() throws Exception {
        Keyword mysql = KeywordFixture.keyword(3L, "MySQL");
        given(articleService.getDetail(1L))
                .willReturn(new ArticleDetailResult(article, List.of(ArticleKeyword.of(article, mysql, "title"))));

        mockMvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MySQL 데드락 디버깅 회고"))
                .andExpect(jsonPath("$.blog.domain").value("techblog.woowahan.com"))
                .andExpect(jsonPath("$.keywords[0].name").value("MySQL"))
                .andExpect(jsonPath("$.keywords[0].matchedVia").value("title"));
    }
}
