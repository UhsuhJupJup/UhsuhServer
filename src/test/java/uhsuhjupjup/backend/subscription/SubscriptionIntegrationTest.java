package uhsuhjupjup.backend.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubscriptionIntegrationTest extends MySqlTestSupport {

    private static final String BEARER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private KeywordRepository keywordRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Member member;
    private Topic database;
    private Keyword kafka;

    @BeforeEach
    void setUp() {
        member = Member.create("github", "uid-int", "int@example.com");
        member.agreeConsent(LocalDateTime.of(2026, 6, 25, 12, 0));
        memberRepository.save(member);
        database = topicRepository.save(Topic.create("Database"));
        kafka = keywordRepository.save(Keyword.create("Kafka"));
        given(firebaseTokenVerifier.verify(anyString()))
                .willReturn(new AuthUser("github", "uid-int", "int@example.com"));
    }

    @Test
    void replace_setsExactSetAndDiffsOnSecondCall() throws Exception {
        mockMvc.perform(put("/api/subscriptions").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicIds\":[" + database.getId() + "],\"keywordIds\":[" + kafka.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics.length()").value(1))
                .andExpect(jsonPath("$.keywords.length()").value(1));

        mockMvc.perform(put("/api/subscriptions").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicIds\":[],\"keywordIds\":[" + kafka.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics.length()").value(0))
                .andExpect(jsonPath("$.keywords[0].name").value("Kafka"));
    }

    @Test
    void replace_withoutConsent_returns403() throws Exception {
        given(firebaseTokenVerifier.verify(anyString()))
                .willReturn(new AuthUser("github", "uid-new", "new@example.com"));

        mockMvc.perform(put("/api/subscriptions").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicIds\":[" + database.getId() + "],\"keywordIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void unsubscribeAll_clearsAll() throws Exception {
        mockMvc.perform(put("/api/subscriptions").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicIds\":[" + database.getId() + "],\"keywordIds\":[" + kafka.getId() + "]}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/subscriptions").header("Authorization", BEARER))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/subscriptions").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics.length()").value(0))
                .andExpect(jsonPath("$.keywords.length()").value(0));
    }

    @Test
    void unsubscribeByToken_clearsAllWithoutAuth() throws Exception {
        mockMvc.perform(put("/api/subscriptions").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicIds\":[" + database.getId() + "],\"keywordIds\":[" + kafka.getId() + "]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/unsubscribe").param("token", member.getUnsubscribeToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unsubscribed").value(true));

        mockMvc.perform(get("/api/subscriptions").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics.length()").value(0))
                .andExpect(jsonPath("$.keywords.length()").value(0));
    }
}
