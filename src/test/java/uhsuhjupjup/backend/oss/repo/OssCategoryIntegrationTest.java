package uhsuhjupjup.backend.oss.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(SharedMySqlTestConfiguration.class)
class OssCategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @Test
    void list_withoutLogin_returnsSeedCategoriesInSeedOrder() throws Exception {
        mockMvc.perform(get("/api/oss/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[*].code", contains(
                        "ai-ml", "ai-agents", "web-frontend", "backend", "mobile", "desktop", "data",
                        "devtools", "infra", "security", "languages", "docs", "games-media")))
                .andExpect(jsonPath("$[*].nameKo", contains(
                        "AI와 머신러닝", "AI 에이전트와 LLM 도구", "웹 프론트엔드", "백엔드와 API", "모바일",
                        "데스크톱 앱", "데이터와 데이터베이스", "개발 도구", "인프라와 클라우드", "보안",
                        "언어와 런타임", "문서와 학습 자료", "게임과 미디어")))
                .andExpect(jsonPath("$[*].nameEn", contains(
                        "AI & Machine Learning", "AI Agents & LLM Tools", "Web Frontend", "Backend & APIs",
                        "Mobile", "Desktop Apps", "Data & Databases", "Developer Tools",
                        "Infrastructure & Cloud", "Security", "Languages & Runtimes", "Docs & Learning",
                        "Games & Media")));
    }
}
