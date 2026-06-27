package uhseojupjup.backend.keyword;

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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KeywordIntegrationTest extends MySqlTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeywordRepository keywordRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() {
        keywordRepository.deleteAll();
        keywordRepository.saveAll(List.of(Keyword.create("Redis"), Keyword.create("Kafka"), Keyword.create("MySQL")));
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
    void list_withQuery_returnsFiltered() throws Exception {
        mockMvc.perform(get("/api/keywords").param("q", "ka"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Kafka"));
    }
}
