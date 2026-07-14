package uhsuhjupjup.backend.keyword;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.domain.KeywordAlias;
import uhsuhjupjup.backend.keyword.infra.KeywordAliasRepository;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.domain.TopicKeyword;
import uhsuhjupjup.backend.topic.infra.TopicKeywordRepository;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KeywordIntegrationTest extends MySqlTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private KeywordAliasRepository keywordAliasRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicKeywordRepository topicKeywordRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Keyword mysql;
    private Topic database;

    @BeforeEach
    void setUp() {
        topicKeywordRepository.deleteAll();
        keywordAliasRepository.deleteAll();
        keywordRepository.deleteAll();
        topicRepository.deleteAll();

        keywordRepository.saveAll(List.of(Keyword.create("Redis"), Keyword.create("Kafka")));
        mysql = keywordRepository.save(Keyword.create("MySQL"));
        keywordAliasRepository.save(KeywordAlias.create(mysql.getId(), "마이에스큐엘"));
        database = topicRepository.save(Topic.create("Database"));
        topicKeywordRepository.save(TopicKeyword.of(database, mysql));
    }

    @Test
    void list_returnsAllOrderedByName() throws Exception {
        mockMvc.perform(get("/api/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Kafka"))
                .andExpect(jsonPath("$[1].name").value("MySQL"))
                .andExpect(jsonPath("$[2].name").value("Redis"));
    }

    @Test
    void list_withQuery_searchesByAlias() throws Exception {
        mockMvc.perform(get("/api/keywords").param("q", "마이"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("MySQL"));
    }

    @Test
    void list_withTopicId_returnsTopicKeywords() throws Exception {
        mockMvc.perform(get("/api/keywords").param("topicId", database.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("MySQL"));
    }

    @Test
    void detail_returnsKeywordWithTopics() throws Exception {
        mockMvc.perform(get("/api/keywords/" + mysql.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MySQL"))
                .andExpect(jsonPath("$.topics.length()").value(1))
                .andExpect(jsonPath("$.topics[0].name").value("Database"));
    }

    @Test
    void detail_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/keywords/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEYWORD_NOT_FOUND"));
    }
}
