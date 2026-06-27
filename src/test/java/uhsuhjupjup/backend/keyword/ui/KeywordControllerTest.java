package uhsuhjupjup.backend.keyword.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.keyword.application.dto.KeywordDetailResult;
import uhsuhjupjup.backend.keyword.application.KeywordService;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.support.KeywordFixture;
import uhsuhjupjup.backend.support.TopicFixture;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KeywordControllerTest {

    @Mock
    private KeywordService keywordService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new KeywordController(keywordService)).build();
    }

    @Test
    void list_returnsKeywords() throws Exception {
        given(keywordService.search(null, null)).willReturn(List.of(KeywordFixture.keyword(3L, "MySQL")));

        mockMvc.perform(get("/api/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("MySQL"));
    }

    @Test
    void list_withQuery_passesQueryToService() throws Exception {
        given(keywordService.search("ka", null)).willReturn(List.of(KeywordFixture.keyword(2L, "Kafka")));

        mockMvc.perform(get("/api/keywords").param("q", "ka"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kafka"));
    }

    @Test
    void list_withTopicId_passesTopicIdToService() throws Exception {
        given(keywordService.search(null, 1L)).willReturn(List.of(KeywordFixture.keyword(3L, "MySQL")));

        mockMvc.perform(get("/api/keywords").param("topicId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("MySQL"));
    }

    @Test
    void detail_returnsKeywordWithTopics() throws Exception {
        Keyword keyword = KeywordFixture.keyword(3L, "MySQL");
        given(keywordService.getDetail(3L))
                .willReturn(new KeywordDetailResult(keyword, List.of(TopicFixture.topic(1L, "Database"))));

        mockMvc.perform(get("/api/keywords/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("MySQL"))
                .andExpect(jsonPath("$.topics[0].name").value("Database"));
    }
}
