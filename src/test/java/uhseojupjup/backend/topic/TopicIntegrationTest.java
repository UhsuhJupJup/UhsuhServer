package uhseojupjup.backend.topic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uhseojupjup.backend.common.auth.FirebaseTokenVerifier;
import uhseojupjup.backend.support.MySqlTestSupport;
import uhseojupjup.backend.topic.domain.Topic;
import uhseojupjup.backend.topic.infra.TopicRepository;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TopicIntegrationTest extends MySqlTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TopicRepository topicRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() {
        topicRepository.deleteAll();
        topicRepository.saveAll(List.of(Topic.create("Database"), Topic.create("Backend"), Topic.create("Infra/DevOps")));
    }

    @Test
    void list_returnsAllOrderedById() throws Exception {
        mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Database"))
                .andExpect(jsonPath("$[1].name").value("Backend"))
                .andExpect(jsonPath("$[2].name").value("Infra/DevOps"));
    }
}
