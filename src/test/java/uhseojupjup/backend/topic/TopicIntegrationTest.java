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
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.keyword.infra.KeywordRepository;
import uhseojupjup.backend.support.MySqlTestSupport;
import uhseojupjup.backend.topic.domain.Topic;
import uhseojupjup.backend.topic.domain.TopicKeyword;
import uhseojupjup.backend.topic.infra.TopicKeywordRepository;
import uhseojupjup.backend.topic.infra.TopicRepository;

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

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private TopicKeywordRepository topicKeywordRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Topic database;

    @BeforeEach
    void setUp() {
        topicKeywordRepository.deleteAll();
        topicRepository.deleteAll();
        keywordRepository.deleteAll();

        database = topicRepository.save(Topic.create("Database"));
        topicRepository.save(Topic.create("Backend"));
        topicRepository.save(Topic.create("Infra/DevOps"));

        Keyword mysql = keywordRepository.save(Keyword.create("MySQL"));
        Keyword redis = keywordRepository.save(Keyword.create("Redis"));
        topicKeywordRepository.save(TopicKeyword.of(database, mysql));
        topicKeywordRepository.save(TopicKeyword.of(database, redis));
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

    @Test
    void detail_returnsTopicWithKeywordsOrderedByName() throws Exception {
        mockMvc.perform(get("/api/topics/" + database.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Database"))
                .andExpect(jsonPath("$.keywords.length()").value(2))
                .andExpect(jsonPath("$.keywords[0].name").value("MySQL"))
                .andExpect(jsonPath("$.keywords[1].name").value("Redis"));
    }

    @Test
    void detail_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/topics/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));
    }
}
