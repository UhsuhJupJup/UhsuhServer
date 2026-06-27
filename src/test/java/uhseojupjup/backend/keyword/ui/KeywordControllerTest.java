package uhseojupjup.backend.keyword.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhseojupjup.backend.keyword.application.KeywordService;
import uhseojupjup.backend.support.KeywordFixture;

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
        given(keywordService.search(null)).willReturn(List.of(KeywordFixture.keyword(3L, "MySQL")));

        mockMvc.perform(get("/api/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("MySQL"));
    }

    @Test
    void list_withQuery_passesQueryToService() throws Exception {
        given(keywordService.search("ka")).willReturn(List.of(KeywordFixture.keyword(2L, "Kafka")));

        mockMvc.perform(get("/api/keywords").param("q", "ka"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kafka"));
    }
}
