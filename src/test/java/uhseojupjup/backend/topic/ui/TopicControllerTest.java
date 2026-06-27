package uhseojupjup.backend.topic.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.support.KeywordFixture;
import uhseojupjup.backend.support.TopicFixture;
import uhseojupjup.backend.topic.application.TopicDetailResult;
import uhseojupjup.backend.topic.application.TopicService;
import uhseojupjup.backend.topic.domain.Topic;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TopicControllerTest {

    @Mock
    private TopicService topicService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TopicController(topicService)).build();
    }

    @Test
    void list_returnsTopics() throws Exception {
        given(topicService.findAll()).willReturn(List.of(TopicFixture.topic(1L, "Database")));

        mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Database"));
    }

    @Test
    void detail_returnsTopicWithKeywords() throws Exception {
        Topic topic = TopicFixture.topic(1L, "Database");
        List<Keyword> keywords = List.of(KeywordFixture.keyword(3L, "MySQL"), KeywordFixture.keyword(1L, "Redis"));
        given(topicService.getDetail(1L)).willReturn(new TopicDetailResult(topic, keywords));

        mockMvc.perform(get("/api/topics/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Database"))
                .andExpect(jsonPath("$.keywords[0].name").value("MySQL"))
                .andExpect(jsonPath("$.keywords[1].name").value("Redis"));
    }
}
